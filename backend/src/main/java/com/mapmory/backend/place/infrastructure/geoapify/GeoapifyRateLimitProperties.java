package com.mapmory.backend.place.infrastructure.geoapify;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "geoapify.rate-limit")
public record GeoapifyRateLimitProperties(
        int searchesPerMemberPerMinute,
        int selectionsPerMemberPerDay,
        int searchesPerDay,
        int selectionsPerDay,
        int requestsPerDay,
        int requestsPerSecond
) {
    public GeoapifyRateLimitProperties {
        if (searchesPerMemberPerMinute < 1 || selectionsPerMemberPerDay < 1
                || searchesPerDay < 1 || selectionsPerDay < selectionsPerMemberPerDay
                || requestsPerDay <= searchesPerDay + selectionsPerDay || requestsPerSecond < 1) {
            throw new IllegalArgumentException("Geoapify 요청 한도 설정이 올바르지 않습니다.");
        }
    }
}
