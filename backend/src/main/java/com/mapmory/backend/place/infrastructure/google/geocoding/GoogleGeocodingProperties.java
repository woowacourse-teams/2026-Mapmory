package com.mapmory.backend.place.infrastructure.google.geocoding;

import com.mapmory.backend.place.infrastructure.google.GoogleCircuitBreakerProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 사진 좌표 → 지역(Geocoding API v4) 설정(google.geocoding). 키와 타임아웃은 공통 설정(google.*)을 쓴다.
 *
 * @param baseUrl        호스트 주소. 테스트만 가짜 서버로 바꾼다.
 * @param rateLimit      분당 한도와 월 한도
 * @param circuitBreaker 역지오코딩 전용 서킷 브레이커
 */
@ConfigurationProperties(prefix = "google.geocoding")
record GoogleGeocodingProperties(
        @DefaultValue("https://geocode.googleapis.com") String baseUrl,
        RateLimit rateLimit,
        GoogleCircuitBreakerProperties circuitBreaker
) {

    GoogleGeocodingProperties {
        if (rateLimit == null || circuitBreaker == null) {
            throw new IllegalArgumentException("google.geocoding.rate-limit과 google.geocoding.circuit-breaker 설정이 필요합니다.");
        }
    }

    record RateLimit(int requestsPerMinute, int perMonth, long freeAlertAt) {

        RateLimit {
            if (requestsPerMinute < 1 || perMonth < 1 || freeAlertAt < 1) {
                throw new IllegalArgumentException("google.geocoding.rate-limit 설정이 올바르지 않습니다.");
            }
        }
    }
}
