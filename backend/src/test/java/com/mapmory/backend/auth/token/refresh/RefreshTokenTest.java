package com.mapmory.backend.auth.token.refresh;

import static org.assertj.core.api.Assertions.assertThat;

import com.mapmory.backend.member.AuthProvider;
import com.mapmory.backend.member.Member;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RefreshTokenTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 25, 12, 0);

    @Test
    void 이미_폐기된_토큰을_다시_폐기해도_처음_폐기된_시각을_그대로_둔다() {
        RefreshToken token = RefreshToken.issue(member(), "hash", NOW.plusDays(14));
        token.revoke(NOW);

        token.revoke(NOW.plusHours(1));

        assertThat(token.getRevokedAt()).isEqualTo(NOW);
    }

    private static Member member() {
        return Member.ofOAuth(AuthProvider.KAKAO, "100001", "소현", UUID.randomUUID());
    }
}
