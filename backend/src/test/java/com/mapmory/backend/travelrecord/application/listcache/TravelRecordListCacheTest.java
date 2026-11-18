package com.mapmory.backend.travelrecord.application.listcache;

import static org.assertj.core.api.Assertions.assertThat;

import com.mapmory.backend.travelrecord.application.TravelRecordListPage;
import com.mapmory.backend.common.cache.DeploymentCacheControl;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.time.LocalDate;
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

@DisplayName("일지 목록 캐시")
class TravelRecordListCacheTest {

    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final TravelRecordListCache cache = new TravelRecordListCache(
            new TravelRecordListCacheProperties(100, Duration.ofMinutes(30)),
            meterRegistry,
            new DeploymentCacheControl(DeploymentCacheControl.Mode.NORMAL)
    );

    @Test
    @DisplayName("한 회원의 모든 필터·페이지를 비워도 다른 회원 캐시는 남는다")
    void evictsOnlyThatMember() {
        AtomicInteger loads = new AtomicInteger();

        cache.get(key(1L, null, 0), () -> load(loads));
        cache.get(key(1L, "KR", 0), () -> load(loads));
        cache.get(key(1L, null, 1), () -> load(loads));
        cache.get(key(2L, null, 0), () -> load(loads));
        cache.evictMember(1L);
        cache.get(key(1L, null, 0), () -> load(loads));
        cache.get(key(1L, "KR", 0), () -> load(loads));
        cache.get(key(1L, null, 1), () -> load(loads));
        cache.get(key(2L, null, 0), () -> load(loads));

        assertThat(loads).hasValue(7);
    }

    @Test
    @DisplayName("적중과 미스를 cache.gets 메트릭으로 남긴다")
    void recordsHitAndMiss() {
        AtomicInteger loads = new AtomicInteger();

        cache.get(key(1L, null, 0), () -> load(loads));
        cache.get(key(1L, null, 0), () -> load(loads));

        assertThat(meterRegistry.get("cache.gets").tag("cache", "travelRecordList").tag("result", "hit")
                .functionCounter().count()).isEqualTo(1.0);
        assertThat(meterRegistry.get("cache.gets").tag("cache", "travelRecordList").tag("result", "miss")
                .functionCounter().count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("일지 수 합이 상한을 넘으면 덜 쓰인 페이지를 버려 상한 안으로 맞춘다")
    void evictsByEntryCount() {
        TravelRecordListCache small = new TravelRecordListCache(
                new TravelRecordListCacheProperties(30, Duration.ofMinutes(30)),
                new SimpleMeterRegistry(),
                new DeploymentCacheControl(DeploymentCacheControl.Mode.NORMAL)
        );

        for (long memberId = 1; memberId <= 5; memberId++) {
            small.get(key(memberId, null, 0), () -> page(10));
        }

        assertThat(small.weightedSize()).isLessThanOrEqualTo(30);
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
            Future<TravelRecordListPage> staleLoad = executor.submit(() -> cache.get(key(1L, null, 0), () -> {
                loading.countDown();
                awaitEviction(evicted);
                return page(1);
            }));
            assertThat(loading.await(5, TimeUnit.SECONDS)).isTrue();

            cache.evictMember(1L);
            evicted.countDown();
            staleLoad.get(5, TimeUnit.SECONDS);

            assertThat(cache.get(key(1L, null, 0), () -> page(2)).entries())
                    .as("무효화 뒤 조회는 새로 읽어야 한다").hasSize(2);
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

    private TravelRecordListCache.Key key(Long memberId, String countryCode, int page) {
        return new TravelRecordListCache.Key(memberId, countryCode, null, null, null, page, 20);
    }

    private TravelRecordListPage load(AtomicInteger loads) {
        loads.incrementAndGet();
        return page(1);
    }

    private TravelRecordListPage page(int count) {
        List<TravelRecordListPage.Entry> entries = LongStream.rangeClosed(1, count)
                .mapToObj(id -> new TravelRecordListPage.Entry(id, "일지" + id, "제주시", null, null, null,
                        LocalDate.of(2026, 10, 1), null, null, 0, List.of()))
                .toList();
        return new TravelRecordListPage(entries, 0, 20, count, 1, false);
    }
}
