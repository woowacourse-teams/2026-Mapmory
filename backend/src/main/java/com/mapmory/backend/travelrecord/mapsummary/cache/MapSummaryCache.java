package com.mapmory.backend.travelrecord.mapsummary.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.mapmory.backend.travelrecord.mapsummary.RegionMapSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.cache.CaffeineCacheMetrics;
import java.util.List;
import java.util.function.Supplier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 회원별 지도 요약을 서버 메모리에 둔다.
 *
 * <p>지도 화면은 앱을 열 때마다 부르지만 요약이 바뀌는 건 그 회원이 일지나 태그를 바꿀 때뿐이다.
 * 바뀌면 {@link MapSummaryCacheInvalidator}가 그 회원 것을 모두 지운다. 서버가 한 대라 메모리로 충분하고,
 * 여러 대가 되면 다른 서버의 캐시는 지워지지 않으므로 TTL만큼 옛 값이 보일 수 있다.
 */
@Component
@EnableConfigurationProperties(MapSummaryCacheProperties.class)
public class MapSummaryCache {

    static final String NAME = "mapSummary";

    private final Cache<Key, List<RegionMapSummary>> cache;

    public MapSummaryCache(MapSummaryCacheProperties properties, MeterRegistry meterRegistry) {
        this.cache = Caffeine.newBuilder()
                .maximumSize(properties.maximumSize())
                .expireAfterWrite(properties.timeToLive())
                .recordStats()
                .build();
        CaffeineCacheMetrics.monitor(meterRegistry, cache, NAME);
    }

    public List<RegionMapSummary> get(Long memberId, Long parentRegionId, Long tagId,
                                      Supplier<List<RegionMapSummary>> loader) {
        return cache.get(new Key(memberId, parentRegionId, tagId), ignored -> List.copyOf(loader.get()));
    }

    public void evictMember(Long memberId) {
        cache.asMap().keySet().removeIf(key -> key.memberId().equals(memberId));
    }

    private record Key(Long memberId, Long parentRegionId, Long tagId) {
    }
}
