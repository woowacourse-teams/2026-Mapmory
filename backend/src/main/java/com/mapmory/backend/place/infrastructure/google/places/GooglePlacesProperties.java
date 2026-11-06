package com.mapmory.backend.place.infrastructure.google.places;

import com.mapmory.backend.place.infrastructure.google.GoogleCircuitBreakerProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 장소 검색(Places API New) 설정(google.places). 키와 타임아웃은 공통 설정(google.*)을 쓴다.
 *
 * @param baseUrl        호스트 주소. 테스트만 가짜 서버로 바꾼다.
 * @param rateLimit      분당 한도(자동완성·상세 합쳐서)와 SKU별 월 한도
 * @param circuitBreaker 장소 검색 전용 서킷 브레이커
 */
@ConfigurationProperties(prefix = "google.places")
record GooglePlacesProperties(
        @DefaultValue("https://places.googleapis.com") String baseUrl,
        RateLimit rateLimit,
        GoogleCircuitBreakerProperties circuitBreaker
) {

    GooglePlacesProperties {
        if (rateLimit == null || circuitBreaker == null) {
            throw new IllegalArgumentException("google.places.rate-limit과 google.places.circuit-breaker 설정이 필요합니다.");
        }
    }

    record RateLimit(int requestsPerMinute, int autocompletePerMonth, int detailsPerMonth, long freeAlertAt) {

        RateLimit {
            if (requestsPerMinute < 1 || autocompletePerMonth < 1 || detailsPerMonth < 1 || freeAlertAt < 1) {
                throw new IllegalArgumentException("google.places.rate-limit 설정이 올바르지 않습니다.");
            }
        }
    }
}
