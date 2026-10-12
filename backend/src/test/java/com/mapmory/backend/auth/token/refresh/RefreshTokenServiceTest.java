package com.mapmory.backend.auth.token.refresh;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

import com.mapmory.backend.auth.token.jwt.JwtProperties;
import com.mapmory.backend.member.AuthProvider;
import com.mapmory.backend.member.Member;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * refresh 만료 정책 검증.
 *
 * 게스트는 다시 로그인할 수단이 없어 만료되면 복구가 불가능하므로 회원보다 긴 만료를 받는다. (ADR 0015)
 */
class RefreshTokenServiceTest {

    private static final Duration MEMBER_VALIDITY = Duration.ofDays(14);
    private static final Duration GUEST_VALIDITY = Duration.ofDays(365);

    private final RefreshTokenRepository refreshTokenRepository = mock(RefreshTokenRepository.class);
    private final RefreshTokenService refreshTokenService = new RefreshTokenService(
            refreshTokenRepository,
            new JwtProperties(
                    "test-secret-key-must-be-long-enough-0123456789",
                    Duration.ofMinutes(30),
                    MEMBER_VALIDITY,
                    GUEST_VALIDITY
            )
    );

    @Test
    void 게스트의_refresh는_게스트용_만료를_받는다() {
        Member guest = Member.ofGuest(UUID.randomUUID().toString(), "회원12345", UUID.randomUUID());

        refreshTokenService.issue(guest);

        assertThat(savedToken().getExpiresAt())
                .isAfter(LocalDateTime.now().plus(GUEST_VALIDITY).minusMinutes(1))
                .isBefore(LocalDateTime.now().plus(GUEST_VALIDITY).plusMinutes(1));
    }

    @Test
    void 소셜_회원의_refresh는_기본_만료를_받는다() {
        Member member = Member.ofOAuth(AuthProvider.KAKAO, "100001", "소현", UUID.randomUUID());

        refreshTokenService.issue(member);

        assertThat(savedToken().getExpiresAt())
                .isAfter(LocalDateTime.now().plus(MEMBER_VALIDITY).minusMinutes(1))
                .isBefore(LocalDateTime.now().plus(MEMBER_VALIDITY).plusMinutes(1));
    }

    private RefreshToken savedToken() {
        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        then(refreshTokenRepository).should().save(captor.capture());
        return captor.getValue();
    }

    @Test
    void 발급된_원문은_매번_다르다() {
        Member guest = Member.ofGuest(UUID.randomUUID().toString(), "회원12345", UUID.randomUUID());

        String first = refreshTokenService.issue(guest);
        String second = refreshTokenService.issue(guest);

        assertThat(first).isNotEqualTo(second);
        then(refreshTokenRepository).should(org.mockito.Mockito.times(2)).save(any(RefreshToken.class));
    }
}
