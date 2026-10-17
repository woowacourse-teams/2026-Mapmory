package com.mapmory.backend.place.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "place.rate-limit")
record PlaceRateLimitProperties(
        int searchesPerMemberPerMinute,
        int selectionsPerMemberPerDay
) {
    PlaceRateLimitProperties {
        if (searchesPerMemberPerMinute < 1 || selectionsPerMemberPerDay < 1) {
            throw new IllegalArgumentException("장소 회원 한도 설정이 올바르지 않습니다.");
        }
    }
}
