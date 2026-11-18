package com.mapmory.backend.common.cache;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("회원별 캐시 세대")
class MemberCacheGenerationsTest {

    private static final Duration TTL = Duration.ofMinutes(30);

    private final AtomicLong nanos = new AtomicLong();
    private final MemberCacheGenerations generations = new MemberCacheGenerations(TTL, nanos::get);

    @Test
    @DisplayName("무효화하기 전까지는 같은 세대라 그사이 조회가 같은 항목을 읽는다")
    void staysUntilAdvanced() {
        long first = generations.current(1L);

        assertThat(generations.current(1L)).isEqualTo(first);
    }

    @Test
    @DisplayName("무효화하면 그 회원의 세대만 바뀐다")
    void advancesOnlyThatMember() {
        long member1 = generations.current(1L);
        long member2 = generations.current(2L);

        generations.advance(1L);

        assertThat(generations.current(1L)).isNotEqualTo(member1);
        assertThat(generations.current(2L)).isEqualTo(member2);
    }

    /**
     * 조회가 세대를 읽은 뒤 오래 걸려 끝나면 그 항목은 늦게 만료된다. 그사이 세대가 만료되어 다시 만들어질 때
     * 처음 값으로 돌아가면, 옛 항목이 다시 읽힌다. 세대는 한 번도 쓰지 않은 번호로 다시 만들어져야 한다.
     */
    @Test
    @DisplayName("세대가 만료되어 다시 만들어져도 그전에 쓴 세대와 겹치지 않는다")
    void neverReusesGenerationAfterExpiry() {
        long beforeWrite = generations.current(1L);
        generations.advance(1L);
        long afterWrite = generations.current(1L);

        nanos.addAndGet(TTL.multipliedBy(3).toNanos());

        assertThat(generations.current(1L)).isNotIn(beforeWrite, afterWrite);
    }
}
