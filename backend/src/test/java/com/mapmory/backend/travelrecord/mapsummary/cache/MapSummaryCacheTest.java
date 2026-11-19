package com.mapmory.backend.travelrecord.mapsummary.cache;

import static org.assertj.core.api.Assertions.assertThat;

import com.mapmory.backend.region.domain.RegionType;
import com.mapmory.backend.common.cache.DeploymentCacheControl;
import com.mapmory.backend.common.cache.DeploymentCacheEndpoint;
import com.mapmory.backend.travelrecord.mapsummary.RegionMapSummary;
import com.mapmory.backend.travelrecord.mapsummary.policy.MapColorLevel;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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

    @Test
    @DisplayName("전체 요약은 계층 요약과 키가 겹치지 않고 자기 결과를 돌려준다")
    void getAllReturnsItsOwnResultSeparateFromRootLevel() {
        List<RegionMapSummary> root = rows(1);
        List<RegionMapSummary> all = rows(3);

        assertThat(cache.get(1L, null, null, () -> root)).isEqualTo(root);
        assertThat(cache.getAll(1L, null, () -> all)).isEqualTo(all);
        assertThat(cache.getAll(1L, null, () -> rows(9))).isEqualTo(all);
        assertThat(cache.get(1L, null, null, () -> rows(9))).isEqualTo(root);
    }

    @Test
    @DisplayName("조회 중에 배포 캐시 모드가 바뀌면 그 조회 결과를 남기지 않는다")
    void dropsLoadWhoseDeploymentGenerationChanged() {
        DeploymentCacheControl control = new DeploymentCacheControl(DeploymentCacheControl.Mode.NORMAL);
        DeploymentCacheEndpoint endpoint = new DeploymentCacheEndpoint(control);
        MapSummaryCache switching = new MapSummaryCache(
                new MapSummaryCacheProperties(100, Duration.ofMinutes(10)), new SimpleMeterRegistry(), control);

        List<RegionMapSummary> result = switching.get(1L, null, null, () -> {
            endpoint.changeMode(DeploymentCacheControl.Mode.BYPASS);
            return rows(4);
        });

        assertThat(result).hasSize(4);
        assertThat(switching.weightedSize()).isZero();
    }

    /**
     * 조회가 DB에서 옛 값을 읽은 뒤 캐시에 넣기 전에, 같은 회원의 기록 생성이 커밋되고 무효화가 도는 경우다.
     * Caffeine은 계산 중인 항목을 asMap() 순회에 보여 주지 않으므로 removeIf가 그 항목을 지우지 못하고,
     * 계산이 끝나면 옛 값이 TTL 동안 남는다. 조회를 loader 안에서 멈춰 이 순서를 고정한다.
     */
    @Test
    @DisplayName("무효화보다 먼저 시작한 조회가 늦게 끝나도 옛 값을 캐시에 남기지 않는다")
    void doesNotKeepLoadStartedBeforeEviction() throws Exception {
        CountDownLatch loading = new CountDownLatch(1);
        CountDownLatch evicted = new CountDownLatch(1);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<List<RegionMapSummary>> staleLoad = executor.submit(() -> cache.get(1L, null, null, () -> {
                loading.countDown();
                awaitEviction(evicted);
                return rows(1);
            }));
            assertThat(loading.await(5, TimeUnit.SECONDS)).isTrue();

            cache.evictMember(1L);
            evicted.countDown();
            staleLoad.get(5, TimeUnit.SECONDS);

            assertThat(cache.get(1L, null, null, () -> rows(2))).as("무효화 뒤 조회는 새로 읽어야 한다").hasSize(2);
        } finally {
            executor.shutdownNow();
        }
    }

    // 무효화 신호를 받기 전에는 loader가 끝나면 안 된다. 그냥 지나가면 무효화보다 먼저 끝난 조회를 보게 되어 고치기 전 코드도 통과한다.
    // 실패는 cache.get을 거쳐 staleLoad.get으로 드러난다.
    private static void awaitEviction(CountDownLatch evicted) {
        try {
            if (!evicted.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("loader가 무효화 신호를 받지 못했다");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("loader가 무효화 신호를 기다리다 중단됐다", exception);
        }
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
