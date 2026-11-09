package com.mapmory.backend.place.infrastructure.google.resilience;

import com.mapmory.backend.common.exception.BusinessException;
import com.mapmory.backend.place.exception.PlaceErrorCode;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.BucketExceptions.BucketExecutionException;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.TokensInheritanceStrategy;
import io.github.bucket4j.distributed.BucketProxy;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import java.time.Clock;
import java.time.Duration;
import java.time.YearMonth;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Google SKU 하나의 월 한도. Google 월 무료 사용량이 SKU마다 따로라(자동완성·상세·Geocoding 각 10,000건) 한도도 SKU마다 하나씩 만든다.
 * 서버 전체가 함께 세도록 DB 버킷(place_rate_limit_bucket)에 쓴다. 달이 바뀌면 키(연월)가 바뀌어 새로 센다.
 *
 * <p>상한에 닿으면 막고, 80%를 쓴 순간과 다 쓴 순간, 무료 사용량에 가까워진 순간(freeAlertAt번째)에 ERROR 로그를 한 번씩 남긴다.
 * 운영의 ERROR 로그 알람이 Discord로 알린다.
 */
public final class GoogleMonthlyBudget {

    private static final Logger log = LoggerFactory.getLogger(GoogleMonthlyBudget.class);

    private final ProxyManager<String> bucketManager;
    private final String logEvent;
    private final String name;
    private final BucketConfiguration configuration;
    private final long freeAlertAt;
    private final PlaceErrorCode exhaustedError;
    private final Clock clock;
    // 무료 사용량 알림을 보낸 월 키. 환불로 남은 개수가 되돌아가도 같은 달에 다시 울리지 않게 한다(서버마다 한 번).
    private final Map<String, Boolean> freeUsageAlerted = new ConcurrentHashMap<>();

    /**
     * @param logEvent       로그 이벤트 앞부분(GOOGLE_PLACES, GOOGLE_GEOCODING). 뒤에 _BUDGET, _FREE_USAGE가 붙는다.
     * @param name           월 키와 로그에 쓰는 이름(autocomplete, details, geocode). 키는 google:{name}:{UTC 연월}이다.
     * @param exhaustedError 상한에 닿았을 때 답할 에러
     */
    public GoogleMonthlyBudget(ProxyManager<String> bucketManager, String logEvent, String name, int capacity,
                               long freeAlertAt, PlaceErrorCode exhaustedError, Clock clock) {
        if (capacity < 1) {
            throw new IllegalArgumentException("Google 월 한도는 1 이상이어야 합니다: " + name);
        }
        this.bucketManager = bucketManager;
        this.logEvent = logEvent;
        this.name = name;
        // 한 달 안에 다시 차지 않도록 채우는 주기를 가장 긴 달로 둔다.
        this.configuration = BucketConfiguration.builder()
                .addLimit(bandwidth -> bandwidth.capacity(capacity).refillIntervally(capacity, Duration.ofDays(31)))
                .build();
        this.freeAlertAt = freeAlertAt;
        this.exhaustedError = exhaustedError;
        this.clock = clock;
    }

    public void consume() {
        ConsumptionProbe probe;
        try {
            probe = bucket().tryConsumeAndReturnRemaining(1);
        } catch (BucketExecutionException exception) {
            // 한도 저장소가 장애일 때 외부 API를 호출하면 사용량을 통제할 수 없다.
            log.atError()
                    .addKeyValue("event", "PLACE_RATE_LIMIT_STORE_UNAVAILABLE")
                    .addKeyValue("budget", name)
                    .setCause(exception)
                    .log("장소 한도 저장소를 쓰지 못했다");
            throw new BusinessException(PlaceErrorCode.PLACE_PROVIDER_UNAVAILABLE);
        }
        if (!probe.isConsumed()) {
            throw new BusinessException(exhaustedError);
        }
        alertIfRunningOut(probe.getRemainingTokens());
        alertIfFreeUsageRunningOut(probe.getRemainingTokens());
    }

    /**
     * Google이 과금하지 않은 호출은 돌려준다. 돌려주지 못하면 실제보다 많이 쓴 것으로 셀 뿐이라,
     * 이미 정해진 Google 장애 응답을 돌려주기 실패로 바꾸지 않는다.
     */
    public void refund() {
        try {
            bucket().addTokens(1);
        } catch (BucketExecutionException exception) {
            log.atWarn()
                    .addKeyValue("event", logEvent + "_BUDGET_REFUND_FAILED")
                    .addKeyValue("budget", name)
                    .setCause(exception)
                    .log("Google {} 월 한도를 돌려주지 못했다", name);
        }
    }

    public long capacity() {
        return configuration.getBandwidths()[0].getCapacity();
    }

    /**
     * 이번 달 버킷. 상한을 바꿔 배포하면 이미 만든 이번 달 버킷에도 새 상한을 적용한다. 버전은 상한 값이라 올렸을 때만
     * 바뀌고, ADDITIVE라 이미 쓴 개수는 그대로 두고 늘어난 만큼만 남은 개수에 더한다. 상한을 낮추면 다음 달부터 적용된다.
     */
    private BucketProxy bucket() {
        return bucketManager.builder()
                .withImplicitConfigurationReplacement(capacity(), TokensInheritanceStrategy.ADDITIVE)
                .build(key(), () -> configuration);
    }

    // Google 월 사용량은 태평양 시간 기준이라 UTC 월과 몇 시간 어긋난다. 월 한도를 무료 사용량보다 작게 둔 이유다.
    private String key() {
        return "google:" + name + ":" + YearMonth.now(clock);
    }

    /** 버킷은 한 번에 한 요청만 꺼내므로 남은 개수가 그 값이 되는 요청은 달마다 하나뿐이다. */
    private void alertIfRunningOut(long remaining) {
        long capacity = capacity();
        boolean exhausted = remaining == 0;
        boolean eightyPercentUsed = capacity >= 5 && remaining == capacity / 5;
        if (!exhausted && !eightyPercentUsed) {
            return;
        }
        log.atError()
                .addKeyValue("event", logEvent + "_BUDGET")
                .addKeyValue("budget", name)
                .addKeyValue("remaining", remaining)
                .addKeyValue("capacity", capacity)
                .log(exhausted
                                ? "Google {} 월 한도를 다 썼다: 남은 {}/{}"
                                : "Google {} 월 한도의 80%를 썼다: 남은 {}/{}",
                        name, remaining, capacity);
    }

    /**
     * 상한이 Google 월 무료 사용량보다 크면, 무료 사용량에 가까워진 순간(free-alert-at번째 호출)에 ERROR 로그를 한 번 남긴다.
     * 이 뒤로는 막지 않고 과금된다. 상한에 닿을 때까지 사람이 보고 판단하라는 알림이다.
     */
    private void alertIfFreeUsageRunningOut(long remaining) {
        long capacity = capacity();
        if (freeAlertAt >= capacity || capacity - remaining != freeAlertAt
                || freeUsageAlerted.putIfAbsent(key(), true) != null) {
            return;
        }
        log.atError()
                .addKeyValue("event", logEvent + "_FREE_USAGE")
                .addKeyValue("budget", name)
                .addKeyValue("used", freeAlertAt)
                .addKeyValue("capacity", capacity)
                .log("Google {} 이번 달 사용량이 {}건이다: 곧 무료 사용량을 넘어 과금된다(상한 {})",
                        name, freeAlertAt, capacity);
    }
}
