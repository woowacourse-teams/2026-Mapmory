package com.mapmory.backend.common.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 회원별 캐시 세대. 회원 단위 무효화를 "항목 지우기" 대신 "세대 바꾸기"로 한다.
 *
 * <p>Caffeine은 계산 중인 항목을 {@code asMap()} 순회에 보여 주지 않는다. 그래서 무효화가 키를 순회해 지우면,
 * 커밋 전에 DB를 읽기 시작해 무효화 뒤에 끝난 조회가 옛 값을 넣어 TTL 동안 남는다.
 * 조회는 시작할 때 이 세대를 키에 넣고, 무효화는 세대를 바꾼다. 늦게 끝난 조회는 옛 세대 키에 들어가 아무도 읽지 않는다.
 * 배포 세대({@link DeploymentCacheControl})와 같은 방식을 회원 단위로 쓴다.
 *
 * <p>세대 값은 회원마다 0부터 세지 않고, 이 객체 전체에서 한 번도 쓰지 않은 번호를 받는다. 세대가 만료되어 다시 만들어져도
 * 아직 살아 있는 옛 항목의 세대와 겹치지 않는다. 그래서 세대를 얼마나 오래 두는지는 정확성과 관계없고,
 * 세대가 먼저 사라지면 그 회원의 다음 조회가 한 번 더 DB를 읽을 뿐이다.
 */
public final class MemberCacheGenerations {

    private final AtomicLong sequence = new AtomicLong();
    private final Cache<Long, Long> generations;

    public MemberCacheGenerations(Duration entryTimeToLive) {
        this(entryTimeToLive, Ticker.systemTicker());
    }

    MemberCacheGenerations(Duration entryTimeToLive, Ticker ticker) {
        this.generations = Caffeine.newBuilder()
                // 마지막으로 읽거나 바꾼 뒤 항목 TTL만큼 둔다. 그동안 조회가 없었다면 그 회원의 항목도 대부분 만료됐다.
                .expireAfterAccess(entryTimeToLive)
                .ticker(ticker)
                .build();
    }

    /**
     * 조회를 시작할 때 키에 넣을 세대. 무효화되기 전까지 같은 값이라 그사이 조회는 같은 항목을 읽는다.
     */
    public long current(Long memberId) {
        return generations.get(memberId, ignored -> sequence.incrementAndGet());
    }

    /**
     * 그 회원의 데이터가 바뀐 뒤(커밋 뒤) 부른다. 새 번호로 바꾸므로 이후 조회는 그전에 채운 항목을 읽지 않는다.
     */
    public void advance(Long memberId) {
        generations.put(memberId, sequence.incrementAndGet());
    }
}
