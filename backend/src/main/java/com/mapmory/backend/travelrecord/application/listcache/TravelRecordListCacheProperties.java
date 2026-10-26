package com.mapmory.backend.travelrecord.application.listcache;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "travel-record.list.cache")
public record TravelRecordListCacheProperties(
        long maximumWeight,
        Duration timeToLive
) {
    public TravelRecordListCacheProperties {
        if (maximumWeight < 1 || timeToLive == null || timeToLive.isNegative() || timeToLive.isZero()) {
            throw new IllegalArgumentException("일지 목록 캐시 설정이 올바르지 않습니다.");
        }
    }
}
