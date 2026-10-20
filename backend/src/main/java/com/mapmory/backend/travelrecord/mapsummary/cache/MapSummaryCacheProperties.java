package com.mapmory.backend.travelrecord.mapsummary.cache;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "travel-record.map-summary.cache")
public record MapSummaryCacheProperties(
        long maximumSize,
        Duration timeToLive
) {
    public MapSummaryCacheProperties {
        if (maximumSize < 1 || timeToLive == null || timeToLive.isNegative() || timeToLive.isZero()) {
            throw new IllegalArgumentException("지도 요약 캐시 설정이 올바르지 않습니다.");
        }
    }
}
