package com.mapmory.backend.travelrecord.application.listcache;

import static org.assertj.core.api.Assertions.assertThat;

import com.mapmory.backend.travelrecord.application.TravelRecordListPage;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.LongStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("일지 목록 캐시")
class TravelRecordListCacheTest {

    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final TravelRecordListCache cache = new TravelRecordListCache(
            new TravelRecordListCacheProperties(100, Duration.ofMinutes(30)),
            meterRegistry
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
                new SimpleMeterRegistry()
        );

        for (long memberId = 1; memberId <= 5; memberId++) {
            small.get(key(memberId, null, 0), () -> page(10));
        }

        assertThat(small.weightedSize()).isLessThanOrEqualTo(30);
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
