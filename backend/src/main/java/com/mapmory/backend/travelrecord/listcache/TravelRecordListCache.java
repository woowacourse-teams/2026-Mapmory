package com.mapmory.backend.travelrecord.listcache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.mapmory.backend.travelrecord.TravelRecordListPage;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.cache.CaffeineCacheMetrics;
import java.util.function.Supplier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 회원별 일지 목록 페이지를 서버 메모리에 둔다.
 *
 * <p>지도 요약과 같은 때(그 회원의 일지·태그가 바뀔 때) {@link TravelRecordListCacheInvalidator}가 그 회원 것을 모두 지운다.
 * 필터와 페이지마다 항목이 따로 생기지만, 지울 때는 회원 단위로 한꺼번에 지운다.
 */
@Component
@EnableConfigurationProperties(TravelRecordListCacheProperties.class)
public class TravelRecordListCache {

    static final String NAME = "travelRecordList";

    private final Cache<Key, TravelRecordListPage> cache;

    public TravelRecordListCache(TravelRecordListCacheProperties properties, MeterRegistry meterRegistry) {
        this.cache = Caffeine.newBuilder()
                // 페이지마다 일지 수가 0~100개로 달라, 페이지 수 대신 일지 수 합으로 메모리를 묶는다.
                .maximumWeight(properties.maximumWeight())
                .weigher((Key key, TravelRecordListPage page) -> Math.max(1, page.entries().size()))
                .expireAfterWrite(properties.timeToLive())
                .recordStats()
                .build();
        CaffeineCacheMetrics.monitor(meterRegistry, cache, NAME);
    }

    public TravelRecordListPage get(Key key, Supplier<TravelRecordListPage> loader) {
        return cache.get(key, ignored -> loader.get());
    }

    public void evictMember(Long memberId) {
        cache.asMap().keySet().removeIf(key -> key.memberId().equals(memberId));
    }

    long weightedSize() {
        cache.cleanUp();
        return cache.policy().eviction().orElseThrow().weightedSize().orElseThrow();
    }

    /**
     * 목록 요청의 조건을 그대로 키로 쓴다. 지역은 코드 그대로 두어 지역을 찾는 조회도 적중하면 건너뛴다.
     */
    public record Key(
            Long memberId,
            String countryCode,
            String provinceCode,
            String districtCode,
            Long tagId,
            int page,
            int size
    ) {
    }
}
