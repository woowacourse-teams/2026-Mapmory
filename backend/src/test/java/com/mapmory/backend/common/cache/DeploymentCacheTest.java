package com.mapmory.backend.common.cache;

import static com.mapmory.backend.common.cache.DeploymentCacheControl.Mode.BYPASS;
import static com.mapmory.backend.common.cache.DeploymentCacheControl.Mode.NORMAL;
import static org.assertj.core.api.Assertions.assertThat;

import com.mapmory.backend.region.domain.RegionType;
import com.mapmory.backend.travelrecord.application.TravelRecordListPage;
import com.mapmory.backend.travelrecord.application.listcache.TravelRecordListCache;
import com.mapmory.backend.travelrecord.application.listcache.TravelRecordListCacheProperties;
import com.mapmory.backend.travelrecord.mapsummary.RegionMapSummary;
import com.mapmory.backend.travelrecord.mapsummary.cache.MapSummaryCache;
import com.mapmory.backend.travelrecord.mapsummary.cache.MapSummaryCacheProperties;
import com.mapmory.backend.travelrecord.mapsummary.policy.MapColorLevel;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

@DisplayName("배포 중 로컬 캐시 제어")
class DeploymentCacheTest {

    private final DeploymentCacheControl control = new DeploymentCacheControl(NORMAL);
    private final DeploymentCacheEndpoint endpoint = new DeploymentCacheEndpoint(control);
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final TravelRecordListCache listCache = new TravelRecordListCache(
            new TravelRecordListCacheProperties(100, Duration.ofMinutes(30)), registry, control);
    private final MapSummaryCache mapCache = new MapSummaryCache(
            new MapSummaryCacheProperties(100, Duration.ofMinutes(30)), registry, control);

    @ParameterizedTest
    @EnumSource(CacheType.class)
    @DisplayName("우회 중에는 기존 값을 읽거나 새 값을 저장하지 않고, 복귀하면 새로 조회한 값을 캐시한다")
    void bypassesReadsAndWritesAndResumesWithFreshData(CacheType type) {
        AtomicLong loads = new AtomicLong();
        assertThat(get(type, loads::incrementAndGet)).isEqualTo(1);
        assertThat(get(type, loads::incrementAndGet)).isEqualTo(1);

        endpoint.changeMode(BYPASS);
        assertThat(get(type, loads::incrementAndGet)).isEqualTo(2);
        assertThat(get(type, loads::incrementAndGet)).isEqualTo(3);

        endpoint.changeMode(NORMAL);
        assertThat(get(type, loads::incrementAndGet)).isEqualTo(4);
        assertThat(get(type, loads::incrementAndGet)).isEqualTo(4);
        assertThat(loads).hasValue(4);
    }

    @ParameterizedTest
    @EnumSource(CacheType.class)
    @DisplayName("이전 세대의 조회가 복귀 후 늦게 끝나도 새 캐시를 덮어쓰지 않는다")
    void lateOldLoadCannotPoisonNewGeneration(CacheType type) throws Exception {
        CountDownLatch loading = new CountDownLatch(1);
        CountDownLatch finish = new CountDownLatch(1);
        try (var executor = Executors.newSingleThreadExecutor()) {
            var oldRequest = executor.submit(() -> get(type, () -> {
                loading.countDown();
                try {
                    if (!finish.await(5, TimeUnit.SECONDS)) {
                        throw new AssertionError("이전 조회 종료 대기 시간 초과");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError(e);
                }
                return 1;
            }));
            try {
                assertThat(loading.await(3, TimeUnit.SECONDS)).isTrue();
                endpoint.changeMode(BYPASS);
                endpoint.changeMode(NORMAL);
                assertThat(get(type, () -> 2)).isEqualTo(2);
            } finally {
                finish.countDown();
            }
            assertThat(oldRequest.get(3, TimeUnit.SECONDS)).isEqualTo(1);
            assertThat(get(type, () -> 3)).isEqualTo(2);
        }
    }

    @Test
    @DisplayName("하나의 명령으로 두 캐시의 모드를 함께 바꾼다")
    void changesBothCachesTogether() {
        AtomicLong loads = new AtomicLong();
        get(CacheType.LIST, loads::incrementAndGet);
        get(CacheType.MAP, loads::incrementAndGet);
        endpoint.changeMode(BYPASS);
        assertThat(get(CacheType.LIST, loads::incrementAndGet)).isEqualTo(3);
        assertThat(get(CacheType.MAP, loads::incrementAndGet)).isEqualTo(4);
        assertThat(get(CacheType.LIST, loads::incrementAndGet)).isEqualTo(5);
        assertThat(get(CacheType.MAP, loads::incrementAndGet)).isEqualTo(6);
    }

    @Test
    @DisplayName("같은 모드의 명령을 재시도해도 캐시 세대는 바뀌지 않는다")
    void modeChangesAreIdempotent() {
        assertThat(endpoint.changeMode(NORMAL)).isEqualTo(endpoint.status());
        var bypass = endpoint.changeMode(BYPASS);
        assertThat(bypass.generation()).isEqualTo(1);
        assertThat(endpoint.changeMode(BYPASS)).isEqualTo(bypass);
        assertThat(endpoint.changeMode(NORMAL).generation()).isEqualTo(2);
    }

    @Test
    @DisplayName("새 프로세스를 처음부터 우회 모드로 시작할 수 있다")
    void startsInBypassMode() {
        DeploymentCacheControl bypass = new DeploymentCacheControl(BYPASS);
        var cache = new TravelRecordListCache(
                new TravelRecordListCacheProperties(100, Duration.ofMinutes(30)),
                new SimpleMeterRegistry(), bypass);
        AtomicLong loads = new AtomicLong();
        for (int i = 0; i < 2; i++) {
            cache.get(listKey(), () -> page(loads.incrementAndGet()));
        }
        assertThat(loads).hasValue(2);
    }

    private long get(CacheType type, LongSupplier loader) {
        return switch (type) {
            case LIST -> listCache.get(listKey(), () -> page(loader.getAsLong())).totalElements();
            case MAP -> mapCache.get(1L, null, null, () -> List.of(new RegionMapSummary(
                    1L, "KR", RegionType.COUNTRY, "대한민국", loader.getAsLong(), MapColorLevel.LOW)))
                    .getFirst().recordCount();
        };
    }

    private TravelRecordListCache.Key listKey() {
        return new TravelRecordListCache.Key(1L, null, null, null, null, 0, 20);
    }

    private TravelRecordListPage page(long count) {
        return new TravelRecordListPage(List.of(), 0, 20, count, 1, false);
    }

    enum CacheType { LIST, MAP }
}
