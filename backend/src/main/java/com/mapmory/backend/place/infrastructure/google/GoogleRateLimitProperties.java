package com.mapmory.backend.place.infrastructure.google;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "google.places.rate-limit")
public record GoogleRateLimitProperties(
        int autocompletePerMonth,
        int detailsPerMonth,
        int requestsPerMinute
) {
    public GoogleRateLimitProperties {
        if (autocompletePerMonth < 1 || detailsPerMonth < 1 || requestsPerMinute < 1) {
            throw new IllegalArgumentException("Google 요청 한도 설정이 올바르지 않습니다.");
        }
    }
}
