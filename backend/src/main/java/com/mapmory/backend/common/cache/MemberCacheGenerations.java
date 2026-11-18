package com.mapmory.backend.common.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 회원별 캐시 세대. 회원 단위 무효화를 "항목 지우기" 대신 "세대 올리기"로 한다.
 *
 * <p>Caffeine은 계산 중인 항목을 {@code asMap()} 순회에 보여 주지 않는다. 그래서 무효화가 키를 순회해 지우면,
 * 커밋 전에 DB를 읽기 시작해 무효화 뒤에 끝난 조회가 옛 값을 넣어 TTL 동안 남는다.
 * 조회는 시작할 때 이 세대를 키에 넣고, 무효화는 세대를 올린다. 늦게 끝난 조회는 옛 세대 키에 들어가 아무도 읽지 않는다.
 * 배포 세대({@link DeploymentCacheControl})와 같은 방식을 회원 단위로 쓴다.
 *
 * <p>세대는 캐시 항목보다 오래 남아야 한다. 세대가 먼저 사라져 0부터 다시 세면, 아직 살아 있는 옛 항목과 키가 겹칠 수 있다.
 * 항목은 세대를 읽은 뒤에 쓰이므로, 세대를 마지막으로 읽거나 올린 뒤 항목 TTL의 두 배 동안 두면 그 회원의 항목이 모두 먼저 만료된다.
 */
public final class MemberCacheGenerations {

    private final Cache<Long, AtomicLong> generations;

    public MemberCacheGenerations(Duration entryTimeToLive) {
        this.generations = Caffeine.newBuilder()
                .expireAfterAccess(entryTimeToLive.multipliedBy(2))
                .build();
    }

    /**
     * 조회를 시작할 때 키에 넣을 세대. 한 번도 무효화되지 않은 회원은 0이다.
     */
    public long current(Long memberId) {
        AtomicLong generation = generations.getIfPresent(memberId);
        return generation == null ? 0 : generation.get();
    }

    /**
     * 그 회원의 데이터가 바뀐 뒤(커밋 뒤) 부른다. 이후 조회는 새 세대 키를 찾으므로 그전에 채운 항목을 읽지 않는다.
     */
    public void advance(Long memberId) {
        generations.get(memberId, ignored -> new AtomicLong()).incrementAndGet();
    }
}
