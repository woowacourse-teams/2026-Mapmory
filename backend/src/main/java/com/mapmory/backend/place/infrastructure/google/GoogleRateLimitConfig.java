package com.mapmory.backend.place.infrastructure.google;

import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import java.time.Duration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Google 호출 한도 버킷을 만든다. 확인 순서와 에러 코드는 {@link GoogleRequestLimiter}가 정한다.
 */
@Configuration
@EnableConfigurationProperties(GoogleRateLimitProperties.class)
class GoogleRateLimitConfig {

    static final String REQUEST_PACE = "googleRequestPace";
    static final String AUTOCOMPLETE_BUDGET = "googleAutocompleteBudget";
    static final String DETAILS_BUDGET = "googleDetailsBudget";

    // 분당 한도는 짧은 시간에 몰린 요청이 월 한도를 한꺼번에 쓰지 못하게 하는 값이라 서버마다 메모리에서 따로 센다.
    @Bean(REQUEST_PACE)
    Bucket googleRequestPace(GoogleRateLimitProperties properties) {
        int perMinute = properties.requestsPerMinute();
        return Bucket.builder()
                .addLimit(bandwidth -> bandwidth.capacity(perMinute).refillGreedy(perMinute, Duration.ofMinutes(1)))
                .build();
    }

    // 월 한도는 Google 월 무료 사용량을 지키는 값이라 서버 전체가 함께 세도록 DB 버킷(place_rate_limit_bucket)에 쓴다.
    @Bean(AUTOCOMPLETE_BUDGET)
    BucketConfiguration googleAutocompleteBudget(GoogleRateLimitProperties properties) {
        return monthly(properties.autocompletePerMonth());
    }

    @Bean(DETAILS_BUDGET)
    BucketConfiguration googleDetailsBudget(GoogleRateLimitProperties properties) {
        return monthly(properties.detailsPerMonth());
    }

    // 달이 바뀌면 키(연월)가 바뀌어 새로 센다. 한 달 안에 다시 차지 않도록 채우는 주기를 가장 긴 달로 둔다.
    private static BucketConfiguration monthly(int capacity) {
        return BucketConfiguration.builder()
                .addLimit(bandwidth -> bandwidth.capacity(capacity).refillIntervally(capacity, Duration.ofDays(31)))
                .build();
    }
}
