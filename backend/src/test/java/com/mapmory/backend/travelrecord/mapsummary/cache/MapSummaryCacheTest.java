package com.mapmory.backend.travelrecord.mapsummary.cache;

import static org.assertj.core.api.Assertions.assertThat;

import com.mapmory.backend.region.domain.RegionType;
import com.mapmory.backend.common.cache.DeploymentCacheControl;
import com.mapmory.backend.travelrecord.mapsummary.RegionMapSummary;
import com.mapmory.backend.travelrecord.mapsummary.policy.MapColorLevel;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.LongStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("지도 요약 캐시")
class MapSummaryCacheTest {

    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final MapSummaryCache cache = new MapSummaryCache(
            new MapSummaryCacheProperties(100, Duration.ofMinutes(10)),
            meterRegistry,
            new DeploymentCacheControl(DeploymentCacheControl.Mode.NORMAL)
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

    @Test
    @DisplayName("지역 줄 수 합이 상한을 넘으면 덜 쓰인 항목을 버려 상한 안으로 맞춘다")
    void evictsByRegionRowCount() {
        MapSummaryCache small = new MapSummaryCache(
                new MapSummaryCacheProperties(30, Duration.ofHours(1)),
                new SimpleMeterRegistry(),
                new DeploymentCacheControl(DeploymentCacheControl.Mode.NORMAL)
        );

        for (long memberId = 1; memberId <= 5; memberId++) {
            small.get(memberId, null, null, () -> rows(10));
        }

        assertThat(small.weightedSize()).isLessThanOrEqualTo(30);
    }

    @Test
    @DisplayName("전체 요약 항목도 지역 줄 수로 무게를 매기고, 같은 회원의 계층 항목과 따로 쌓인다")
    void weighsAllScopeByRowsSeparatelyFromLevelScope() {
        AtomicInteger loads = new AtomicInteger();

        cache.get(1L, null, null, () -> rows(1));
        cache.get(1L, 5L, null, () -> rows(17));
        cache.getAll(1L, null, () -> rows(18));
        cache.getAll(1L, null, () -> load(loads));

        assertThat(loads).hasValue(0);
        assertThat(cache.weightedSize()).isEqualTo(1 + 17 + 18);

        cache.evictMember(1L);

        assertThat(cache.weightedSize()).isZero();
    }

    private List<RegionMapSummary> rows(int count) {
        return LongStream.rangeClosed(1, count)
                .mapToObj(id -> new RegionMapSummary(id, null, "R" + id, RegionType.PROVINCE, "지역" + id, 1L, MapColorLevel.LOW))
                .toList();
    }

    private List<RegionMapSummary> load(AtomicInteger loads) {
        loads.incrementAndGet();
        return List.of(new RegionMapSummary(1L, null, "KR", RegionType.COUNTRY, "대한민국", 1L, MapColorLevel.LOW));
    }
}
