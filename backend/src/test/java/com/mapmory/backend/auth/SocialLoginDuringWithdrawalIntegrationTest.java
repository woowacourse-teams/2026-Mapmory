package com.mapmory.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.willReturn;

import com.mapmory.backend.IntegrationTest;
import com.mapmory.backend.auth.application.AuthService;
import com.mapmory.backend.auth.application.model.LoginResult;
import com.mapmory.backend.auth.application.model.SocialIdentity;
import com.mapmory.backend.auth.application.model.SocialProvider;
import com.mapmory.backend.auth.token.refresh.RefreshTokenService;
import com.mapmory.backend.member.AuthProvider;
import com.mapmory.backend.member.Member;
import com.mapmory.backend.member.MemberRepository;
import com.mapmory.backend.member.application.MemberJoinService;
import com.mapmory.backend.member.application.MemberService;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 기존 회원의 탈퇴와 같은 소셜 계정의 로그인이 겹치는 경우. (#584)
 *
 * 로그인이 기존 회원을 잠그지 않고 읽은 뒤 탈퇴가 먼저 커밋되면, 읽은 결과로 그 회원에게 토큰을 발급하고
 * 게스트가 있으면 버린다. 탈퇴한 회원에게 쓸 수 있는 refresh가 남으면 안 된다.
 *
 * 겹치는 순간이 매번 생기지는 않으므로 탈퇴를 시도마다 0~5ms 늦춰 여러 번 시도한다.
 * 테스트 메서드에 트랜잭션을 두지 않는다. 두 요청이 각자 트랜잭션을 연다.
 */
class SocialLoginDuringWithdrawalIntegrationTest extends IntegrationTest {

    private static final int ATTEMPTS = 30;

    @Autowired
    private AuthService authService;

    @Autowired
    private MemberService memberService;

    @Autowired
    private MemberJoinService memberJoinService;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void 탈퇴와_같은_소셜_계정의_로그인이_겹쳐도_탈퇴한_회원에게_쓸_수_있는_refresh가_남지_않는다() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
                String kakaoId = String.valueOf(800_001L + attempt);
                Long memberId = joinWithKakao(kakaoId);

                loginAndWithdrawConcurrently(executor, memberId, null, attempt % 6);

                assertThat(liveRefreshCount(memberId))
                        .as("시도 %d: 탈퇴한 회원에게 쓸 수 있는 refresh가 남으면 안 된다", attempt + 1)
                        .isZero();
            }
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void 게스트가_로그인하는_사이_기존_회원이_탈퇴해도_탈퇴한_회원에게_refresh가_남지_않고_게스트는_승격된다() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
                String kakaoId = String.valueOf(800_101L + attempt);
                Long memberId = joinWithKakao(kakaoId);
                Member guest = memberJoinService.joinAsGuest();

                LoginResult login = loginAndWithdrawConcurrently(executor, memberId, guest.getId(), attempt % 6);

                int tried = attempt + 1;
                assertThat(liveRefreshCount(memberId))
                        .as("시도 %d: 탈퇴한 회원에게 쓸 수 있는 refresh가 남으면 안 된다", tried)
                        .isZero();
                // 로그인이 먼저 끝났다면 게스트는 버려지고 그 토큰은 탈퇴로 폐기된다.
                // 탈퇴가 먼저 끝났다면 기존 회원이 없으므로 게스트가 그 소셜 계정으로 승격되어야 한다.
                if (!login.guestAbandoned()) {
                    assertThat(memberRepository.findById(guest.getId())).get()
                            .as("시도 %d: 버리지 않았다면 승격돼야 한다", tried)
                            .extracting(Member::getProvider)
                            .isEqualTo(AuthProvider.KAKAO);
                    assertThat(refreshTokenService.rotate(login.tokens().refreshToken()))
                            .as("시도 %d: 승격으로 받은 refresh는 유효해야 한다", tried)
                            .isPresent();
                }
            }
        } finally {
            executor.shutdownNow();
        }
    }

    private Long joinWithKakao(String kakaoId) {
        willReturn(new SocialIdentity(kakaoId, "소현")).given(kakaoIdentityAdapter).verify(anyString());
        authService.loginWithSocial(SocialProvider.KAKAO, "kakao-id-token", null);
        return memberRepository.findByProviderAndProviderId(AuthProvider.KAKAO, kakaoId).orElseThrow().getId();
    }

    private LoginResult loginAndWithdrawConcurrently(ExecutorService executor, Long memberId, Long guestId, long withdrawLateBy)
            throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        Future<LoginResult> login = executor.submit(() -> {
            start.await();
            return authService.loginWithSocial(SocialProvider.KAKAO, "kakao-id-token", guestId);
        });
        Future<?> withdrawn = executor.submit(() -> {
            start.await();
            Thread.sleep(withdrawLateBy);
            memberService.withdraw(memberId);
            return null;
        });
        start.countDown();
        withdrawn.get(10, TimeUnit.SECONDS);
        return login.get(10, TimeUnit.SECONDS);
    }

    private int liveRefreshCount(Long memberId) {
        return jdbcTemplate.queryForObject(
                "select count(*) from refresh_token where member_id = ? and revoked_at is null", Integer.class, memberId);
    }
}
