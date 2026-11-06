package com.mapmory.backend.place.infrastructure.google.places;

import com.mapmory.backend.place.infrastructure.google.resilience.GoogleCircuitBreakerProperties;
import java.util.Map;
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

    /**
     * @param perMonth 엔드포인트마다 월 한도(per-month.autocomplete, per-month.details). 모든 엔드포인트에 있어야 한다.
     */
    record RateLimit(int requestsPerMinute, Map<GooglePlacesEndpoint, Integer> perMonth, long freeAlertAt) {

        RateLimit {
            if (requestsPerMinute < 1 || freeAlertAt < 1) {
                throw new IllegalArgumentException("google.places.rate-limit 설정이 올바르지 않습니다.");
            }
            Map<GooglePlacesEndpoint, Integer> limits = perMonth == null ? Map.of() : perMonth;
            for (GooglePlacesEndpoint endpoint : GooglePlacesEndpoint.values()) {
                Integer limit = limits.get(endpoint);
                if (limit == null || limit < 1) {
                    throw new IllegalArgumentException(
                            "google.places.rate-limit.per-month." + endpoint.budgetName() + "이 1 이상이어야 합니다.");
                }
            }
            perMonth = Map.copyOf(limits);
        }
    }
}
