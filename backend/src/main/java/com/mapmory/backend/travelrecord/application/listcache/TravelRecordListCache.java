package com.mapmory.backend.travelrecord.application.listcache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.mapmory.backend.common.cache.DeploymentCacheControl;
import com.mapmory.backend.common.cache.MemberCacheGenerations;
import com.mapmory.backend.travelrecord.application.TravelRecordListPage;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.cache.CaffeineCacheMetrics;
import java.util.function.Supplier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 회원별 일지 목록 페이지를 서버 메모리에 둔다.
 *
 * <p>지도 요약과 같은 때(그 회원의 일지·태그가 바뀔 때) {@link TravelRecordListCacheInvalidator}가 그 회원의 세대를 올려
 * 그전 항목을 읽지 않게 한다({@link MemberCacheGenerations}). 필터와 페이지마다 항목이 따로 생기지만, 무효화는 회원 단위로 한꺼번에 한다.
 */
@Component
@EnableConfigurationProperties(TravelRecordListCacheProperties.class)
public class TravelRecordListCache {

    static final String NAME = "travelRecordList";

    private final Cache<GenerationKey, TravelRecordListPage> cache;
    private final DeploymentCacheControl control;
    private final MemberCacheGenerations memberGenerations;

    public TravelRecordListCache(TravelRecordListCacheProperties properties, MeterRegistry meterRegistry,
                                 DeploymentCacheControl control) {
        this.control = control;
        this.memberGenerations = new MemberCacheGenerations(properties.timeToLive());
        this.cache = Caffeine.newBuilder()
                // 페이지마다 일지 수가 0~100개로 달라, 페이지 수 대신 일지 수 합으로 메모리를 묶는다.
                .maximumWeight(properties.maximumWeight())
                .weigher((GenerationKey key, TravelRecordListPage page) -> Math.max(1, page.entries().size()))
                .expireAfterWrite(properties.timeToLive())
                .recordStats()
                .build();
        CaffeineCacheMetrics.monitor(meterRegistry, cache, NAME);
    }

    public TravelRecordListPage get(Key key, Supplier<TravelRecordListPage> loader) {
        DeploymentCacheControl.Snapshot snapshot = control.snapshot();
        if (snapshot.bypass()) {
            return loader.get();
        }
        // 회원 세대는 DB를 읽기 전에 정한다. 그 뒤에 무효화되면 이 조회는 옛 세대 키에 들어가 다음 조회가 읽지 않는다.
        GenerationKey generationKey = new GenerationKey(
                snapshot.generation(), memberGenerations.current(key.memberId()), key);
        TravelRecordListPage result = cache.get(generationKey, ignored -> loader.get());
        if (control.snapshot().generation() != snapshot.generation()) {
            cache.invalidate(generationKey);
        }
        return result;
    }

    /**
     * 세대를 먼저 올려 이후 조회가 그전 항목을 읽지 않게 한다. 남은 항목은 메모리를 비우려고 지운다.
     * 계산 중인 항목은 순회에 보이지 않아 지우지 못하지만, 옛 세대 키라 읽히지 않고 TTL이나 축출로 사라진다.
     */
    public void evictMember(Long memberId) {
        memberGenerations.advance(memberId);
        cache.asMap().keySet().removeIf(key -> key.key().memberId().equals(memberId));
    }

    long weightedSize() {
        cache.cleanUp();
        return cache.policy().eviction().orElseThrow().weightedSize().orElseThrow();
    }

    private record GenerationKey(long generation, long memberGeneration, Key key) {
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
