package com.mapmory.backend.auth.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class JwtProviderTest {

    private static final String SECRET = "unit-test-secret-key-for-jwt-provider-0123456789";

    private final JwtProvider jwtProvider =
            new JwtProvider(new JwtProperties(SECRET, Duration.ofMinutes(30), Duration.ofDays(14)));

    @Test
    void 발급한_토큰에서_memberId를_다시_읽는다() {
        String token = jwtProvider.issueAccessToken(42L);

        assertThat(jwtProvider.parseMemberId(token)).isEqualTo(42L);
    }

    @Test
    void 만료된_토큰은_ExpiredJwtException을_던진다() {
        JwtProvider expiredProvider =
                new JwtProvider(new JwtProperties(SECRET, Duration.ofSeconds(-1), Duration.ofDays(14)));
        String expiredToken = expiredProvider.issueAccessToken(1L);

        assertThatThrownBy(() -> jwtProvider.parseMemberId(expiredToken))
                .isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void 위조된_토큰은_JwtException을_던진다() {
        String tampered = jwtProvider.issueAccessToken(1L) + "tampered";

        assertThatThrownBy(() -> jwtProvider.parseMemberId(tampered))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void 다른_키로_서명한_토큰은_검증에_실패한다() {
        JwtProvider otherKeyProvider =
                new JwtProvider(new JwtProperties("another-secret-key-totally-different-0123456789", Duration.ofMinutes(30), Duration.ofDays(14)));
        String token = otherKeyProvider.issueAccessToken(1L);

        assertThatThrownBy(() -> jwtProvider.parseMemberId(token))
                .isInstanceOf(JwtException.class);
    }
}
