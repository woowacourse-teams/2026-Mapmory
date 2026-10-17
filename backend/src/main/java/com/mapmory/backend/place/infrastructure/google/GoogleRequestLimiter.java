package com.mapmory.backend.place.infrastructure.google;

import com.mapmory.backend.common.exception.BusinessException;
import com.mapmory.backend.place.application.PlaceErrorCode;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.BucketExceptions.BucketExecutionException;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import java.time.YearMonth;
import java.time.ZoneOffset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * Google 호출 직전에 우리 Google 키 전체의 한도를 확인한다. Google은 월 무료 사용량을 넘으면 과금된다.
 * 회원 한도를 거치지 않는 기록 저장 재조회도 여기서 센다. 회원별 한도는 {@code MemberPlaceRateLimiter}가 따로 센다.
 *
 * <p>버킷은 {@link GoogleRateLimitConfig}가 만든다. 분당 한도는 서버 메모리에서, 월 한도는 DB에서 센다.
 * 그래서 분당 한도에 걸린 요청은 커넥션 없이 바로 거절된다.
 */
@Component
public class GoogleRequestLimiter {

    private static final Logger log = LoggerFactory.getLogger(GoogleRequestLimiter.class);

    private final ProxyManager<String> bucketManager;
    private final Bucket requestPace;
    private final Budget autocomplete;
    private final Budget details;

    public GoogleRequestLimiter(
            ProxyManager<String> bucketManager,
            @Qualifier(GoogleRateLimitConfig.REQUEST_PACE) Bucket requestPace,
            @Qualifier(GoogleRateLimitConfig.AUTOCOMPLETE_BUDGET) BucketConfiguration autocompleteBudget,
            @Qualifier(GoogleRateLimitConfig.DETAILS_BUDGET) BucketConfiguration detailsBudget
    ) {
        this.bucketManager = bucketManager;
        this.requestPace = requestPace;
        this.autocomplete = new Budget("autocomplete", autocompleteBudget, PlaceErrorCode.PLACE_SEARCH_BUDGET_EXHAUSTED);
        this.details = new Budget("details", detailsBudget, PlaceErrorCode.PLACE_PROVIDER_BUDGET_EXHAUSTED);
    }

    // 분당 한도를 먼저 확인한다. 거절된 요청이 DB에 가지 않고 월 한도도 쓰지 않게 하기 위해서다.
    public void reserveAutocomplete() {
        reservePace();
        consume(autocomplete);
    }

    public void reserveDetails() {
        reservePace();
        consume(details);
    }

    /** Google이 과금하지 않은 호출(5xx, 연결 실패)은 월 한도를 돌려준다. 분당 한도는 몰린 요청을 막는 값이라 돌려주지 않는다. */
    public void refundAutocomplete() {
        refund(autocomplete);
    }

    public void refundDetails() {
        refund(details);
    }

    private void reservePace() {
        if (!requestPace.tryConsume(1)) {
            throw new BusinessException(PlaceErrorCode.PLACE_RATE_LIMITED);
        }
    }

    private void consume(Budget budget) {
        ConsumptionProbe probe;
        try {
            probe = bucketManager.getProxy(budget.key(), budget::configuration).tryConsumeAndReturnRemaining(1);
        } catch (BucketExecutionException exception) {
            // 한도 저장소가 장애일 때 외부 API를 호출하면 사용량을 통제할 수 없다.
            throw new BusinessException(PlaceErrorCode.PLACE_PROVIDER_UNAVAILABLE);
        }
        if (!probe.isConsumed()) {
            throw new BusinessException(budget.exhaustedError());
        }
        alertIfRunningOut(budget, probe.getRemainingTokens());
    }

    // 돌려주지 못하면 실제보다 많이 쓴 것으로 셀 뿐이다. 이미 정해진 Google 장애 응답을 돌려주기 실패로 바꾸지 않는다.
    private void refund(Budget budget) {
        try {
            bucketManager.getProxy(budget.key(), budget::configuration).addTokens(1);
        } catch (BucketExecutionException exception) {
            log.atWarn()
                    .addKeyValue("event", "GOOGLE_PLACES_BUDGET_REFUND_FAILED")
                    .addKeyValue("budget", budget.name())
                    .setCause(exception)
                    .log("Google {} 월 한도를 돌려주지 못했다", budget.name());
        }
    }

    /**
     * 월 한도의 80%를 쓴 순간과 다 쓴 순간에 ERROR 로그를 한 번씩 남긴다. 운영의 ERROR 로그 알람이 Discord로 알린다.
     * 버킷은 한 번에 한 요청만 꺼내므로 남은 개수가 그 값이 되는 요청은 달마다 하나뿐이다.
     */
    private static void alertIfRunningOut(Budget budget, long remaining) {
        long capacity = budget.capacity();
        boolean exhausted = remaining == 0;
        boolean eightyPercentUsed = capacity >= 5 && remaining == capacity / 5;
        if (!exhausted && !eightyPercentUsed) {
            return;
        }
        log.atError()
                .addKeyValue("event", "GOOGLE_PLACES_BUDGET")
                .addKeyValue("budget", budget.name())
                .addKeyValue("remaining", remaining)
                .addKeyValue("capacity", capacity)
                .log(exhausted
                                ? "Google {} 월 한도를 다 썼다: 남은 {}/{}"
                                : "Google {} 월 한도의 80%를 썼다: 남은 {}/{}",
                        budget.name(), remaining, capacity);
    }

    private record Budget(String name, BucketConfiguration configuration, PlaceErrorCode exhaustedError) {

        // Google 월 사용량은 태평양 시간 기준이라 UTC 월과 몇 시간 어긋난다. 월 한도를 무료 사용량보다 작게 둔 이유다.
        String key() {
            return "google:" + name + ":" + YearMonth.now(ZoneOffset.UTC);
        }

        long capacity() {
            return configuration.getBandwidths()[0].getCapacity();
        }
    }
}
