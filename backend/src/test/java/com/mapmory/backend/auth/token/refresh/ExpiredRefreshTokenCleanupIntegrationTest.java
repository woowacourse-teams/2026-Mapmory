package com.mapmory.backend.auth.token.refresh;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mapmory.backend.IntegrationTest;
import com.mapmory.backend.auth.exception.AuthErrorCode;
import com.mapmory.backend.common.exception.BusinessException;
import com.mapmory.backend.member.AuthProvider;
import com.mapmory.backend.member.Member;
import com.mapmory.backend.member.MemberRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 만료되고 7일이 지난 refresh 토큰과 토큰이 남지 않은 세션을 지운다. (#454, ADR 0011)
 */
class ExpiredRefreshTokenCleanupIntegrationTest extends IntegrationTest {

    @Autowired
    private ExpiredRefreshTokenCleanup cleanup;

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Autowired
    private RefreshTokenHasher refreshTokenHasher;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private Clock clock;

    private Member member;
    private LocalDateTime now;

    @BeforeEach
    void setUp() {
        now = LocalDateTime.now(clock);
        member = memberRepository.save(
                Member.ofOAuth(AuthProvider.KAKAO, UUID.randomUUID().toString(), "지수", UUID.randomUUID()));
    }

    @Test
    void 만료되고_7일이_지난_토큰과_빈_세션을_지운다() {
        String token = refreshTokenService.issue(member);
        expire(token, now.minusDays(8));

        cleanup.run();

        assertThat(tokenCount()).isZero();
        assertThat(familyCount()).isZero();
    }

    @Test
    void 만료되고_7일이_지나지_않은_토큰은_남긴다() {
        String token = refreshTokenService.issue(member);
        expire(token, now.minusDays(6));

        cleanup.run();

        assertThat(tokenCount()).isEqualTo(1);
        assertThat(familyCount()).isEqualTo(1);
    }

    @Test
    void 만료되지_않은_토큰과_그_세션은_남긴다() {
        refreshTokenService.issue(member);

        cleanup.run();

        assertThat(tokenCount()).isEqualTo(1);
        assertThat(familyCount()).isEqualTo(1);
    }

    /**
     * 오래 쓴 세션의 옛 토큰만 지운다. 세션과 현재 토큰은 계속 쓸 수 있다.
     */
    @Test
    void 살아_있는_세션의_옛_토큰을_지워도_현재_토큰으로_회전한다() {
        String old = refreshTokenService.issue(member);
        String current = refreshTokenService.rotate(old).orElseThrow().refreshToken();
        expire(old, now.minusDays(8));

        cleanup.run();

        assertThat(tokenCount()).isEqualTo(1);
        assertThat(familyCount()).isEqualTo(1);
        assertThat(refreshTokenService.rotate(current)).isPresent();
    }

    /**
     * 지워진 토큰은 없는 토큰과 같다. 이미 쓸 수 없는 토큰이라 세션을 끊지 않고 401로 끝난다.
     */
    @Test
    void 지워진_옛_토큰이_다시_와도_거절만_하고_세션은_유지한다() {
        String old = refreshTokenService.issue(member);
        String current = refreshTokenService.rotate(old).orElseThrow().refreshToken();
        expire(old, now.minusDays(8));
        cleanup.run();

        assertThatThrownBy(() -> refreshTokenService.rotate(old))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(AuthErrorCode.INVALID_REFRESH_TOKEN);

        assertThat(refreshTokenService.rotate(current)).isPresent();
    }

    @Test
    void 한_번에_지우는_양을_넘어도_끝까지_지운다() {
        for (int i = 0; i < 5; i++) {
            expire(refreshTokenService.issue(member), now.minusDays(8));
        }

        cleanup.run(2);

        assertThat(tokenCount()).isZero();
        assertThat(familyCount()).isZero();
    }

    private void expire(String rawToken, LocalDateTime expiresAt) {
        jdbcTemplate.update("UPDATE refresh_token SET expires_at = ? WHERE token_hash = ?",
                expiresAt, refreshTokenHasher.hash(rawToken));
    }

    private long tokenCount() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM refresh_token WHERE member_id = ?", Long.class, member.getId());
    }

    private long familyCount() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM refresh_token_family WHERE member_id = ?", Long.class, member.getId());
    }
}
