package com.mapmory.backend.common.cache;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("회원별 캐시 세대")
class MemberCacheGenerationsTest {

    private final MemberCacheGenerations generations = new MemberCacheGenerations(Duration.ofMinutes(30));

    @Test
    @DisplayName("한 번도 무효화되지 않은 회원은 0세대다")
    void startsAtZero() {
        assertThat(generations.current(1L)).isZero();
    }

    @Test
    @DisplayName("무효화할 때마다 그 회원의 세대만 오른다")
    void advancesOnlyThatMember() {
        generations.advance(1L);
        generations.advance(1L);
        generations.advance(2L);

        assertThat(generations.current(1L)).isEqualTo(2);
        assertThat(generations.current(2L)).isEqualTo(1);
        assertThat(generations.current(3L)).isZero();
    }
}
