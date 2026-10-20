package com.mapmory.backend.travelrecord.mapsummary.cache;

import static org.assertj.core.api.Assertions.assertThat;

import com.mapmory.backend.region.RegionType;
import com.mapmory.backend.travelrecord.mapsummary.RegionMapSummary;
import com.mapmory.backend.travelrecord.mapsummary.policy.MapColorLevel;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("지도 요약 캐시")
class MapSummaryCacheTest {

    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final MapSummaryCache cache = new MapSummaryCache(
            new MapSummaryCacheProperties(100, Duration.ofMinutes(10)),
            meterRegistry
    );

    @Test
    @DisplayName("한 회원 캐시를 비워도 다른 회원 캐시는 남는다")
    void evictsOnlyThatMember() {
        AtomicInteger loads = new AtomicInteger();

        cache.get(1L, null, null, () -> load(loads));
        cache.get(1L, 5L, null, () -> load(loads));
        cache.get(2L, null, null, () -> load(loads));
        cache.evictMember(1L);
        cache.get(1L, null, null, () -> load(loads));
        cache.get(1L, 5L, null, () -> load(loads));
        cache.get(2L, null, null, () -> load(loads));

        assertThat(loads).hasValue(5);
    }

    @Test
    @DisplayName("적중과 미스를 cache.gets 메트릭으로 남긴다")
    void recordsHitAndMiss() {
        AtomicInteger loads = new AtomicInteger();

        cache.get(1L, null, null, () -> load(loads));
        cache.get(1L, null, null, () -> load(loads));
        cache.get(1L, null, null, () -> load(loads));

        assertThat(meterRegistry.get("cache.gets").tag("cache", "mapSummary").tag("result", "hit")
                .functionCounter().count()).isEqualTo(2.0);
        assertThat(meterRegistry.get("cache.gets").tag("cache", "mapSummary").tag("result", "miss")
                .functionCounter().count()).isEqualTo(1.0);
    }

    private List<RegionMapSummary> load(AtomicInteger loads) {
        loads.incrementAndGet();
        return List.of(new RegionMapSummary(1L, "KR", RegionType.COUNTRY, "대한민국", 1L, MapColorLevel.LOW));
    }
}
