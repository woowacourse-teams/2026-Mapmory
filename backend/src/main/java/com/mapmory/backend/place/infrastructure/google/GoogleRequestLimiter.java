package com.mapmory.backend.place.infrastructure.google;

import com.mapmory.backend.common.exception.BusinessException;
import com.mapmory.backend.place.application.PlaceErrorCode;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.BucketExceptions.BucketExecutionException;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Google 호출을 하루 한도로 막는다. Google은 월 무료 사용량을 넘으면 과금되므로 Geoapify 한도와 따로 센다.
 * 회원별 검색·선택 한도는 기존 {@code PlaceRateLimitPort}가 그대로 적용한다.
 */
@Component
public class GoogleRequestLimiter {

    private static final String AUTOCOMPLETE_BUDGET = "google:autocomplete:";
    private static final String DETAILS_BUDGET = "google:details:";
    private static final String REQUEST_PACE = "google:pace";

    private final ProxyManager<String> bucketManager;
    private final BucketConfiguration autocompleteBudget;
    private final BucketConfiguration detailsBudget;
    private final BucketConfiguration requestPace;

    public GoogleRequestLimiter(
            ProxyManager<String> bucketManager,
            @Value("${google.places.rate-limit.autocomplete-per-day:300}") int autocompletePerDay,
            @Value("${google.places.rate-limit.details-per-day:300}") int detailsPerDay,
            @Value("${google.places.rate-limit.requests-per-second:4}") int requestsPerSecond
    ) {
        if (autocompletePerDay < 1 || detailsPerDay < 1 || requestsPerSecond < 1) {
            throw new IllegalArgumentException("Google 요청 한도 설정이 올바르지 않습니다.");
        }
        this.bucketManager = bucketManager;
        this.autocompleteBudget = daily(autocompletePerDay);
        this.detailsBudget = daily(detailsPerDay);
        this.requestPace = BucketConfiguration.builder()
                .addLimit(bandwidth -> bandwidth.capacity(1)
                        .refillGreedy(1, Duration.ofSeconds(1).dividedBy(requestsPerSecond)))
                .build();
    }

    // 속도 제한을 먼저 확인한다. 거절된 요청이 하루 한도를 쓰지 않게 하기 위해서다.
    public void reserveAutocomplete() {
        consume(REQUEST_PACE, requestPace, PlaceErrorCode.PLACE_RATE_LIMITED);
        consume(AUTOCOMPLETE_BUDGET + today(), autocompleteBudget, PlaceErrorCode.PLACE_SEARCH_BUDGET_EXHAUSTED);
    }

    public void reserveDetails() {
        consume(REQUEST_PACE, requestPace, PlaceErrorCode.PLACE_RATE_LIMITED);
        consume(DETAILS_BUDGET + today(), detailsBudget, PlaceErrorCode.PLACE_PROVIDER_BUDGET_EXHAUSTED);
    }

    private void consume(String key, BucketConfiguration configuration, PlaceErrorCode exhaustedError) {
        try {
            if (!bucketManager.getProxy(key, () -> configuration).tryConsume(1)) {
                throw new BusinessException(exhaustedError);
            }
        } catch (BucketExecutionException exception) {
            // 한도 저장소가 장애일 때 외부 API를 호출하면 사용량을 통제할 수 없다.
            throw new BusinessException(PlaceErrorCode.PLACE_PROVIDER_UNAVAILABLE);
        }
    }

    private static String today() {
        return LocalDate.now(ZoneOffset.UTC).toString();
    }

    private static BucketConfiguration daily(int capacity) {
        return BucketConfiguration.builder()
                .addLimit(bandwidth -> bandwidth.capacity(capacity).refillIntervally(capacity, Duration.ofDays(1)))
                .build();
    }
}
