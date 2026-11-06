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
import com.mapmory.backend.auth.token.refresh.RotatedRefreshToken;
import com.mapmory.backend.member.Member;
import com.mapmory.backend.member.application.MemberJoinService;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 게스트가 소셜 로그인으로 승격될 때, 게스트로 받아 둔 refresh가 다시 오는 경우. (#579)
 *
 * 승격하면 게스트 refresh를 모두 폐기한다. 앱이 저장해 둔 게스트 refresh로 갱신을 시도하는 것은 탈취가 아니므로,
 * 방금 받은 소셜 refresh까지 폐기하면 안 된다.
 *
 * 이미 가입한 소셜 계정으로 로그인해 게스트가 버려질 때도 게스트 refresh를 모두 폐기하므로, 동시성은 둘 다 확인한다. (ADR 0028)
 *
 * loginWithSocial은 트랜잭션 밖에서 불러야 하므로(Propagation.NEVER) 테스트 메서드에 트랜잭션을 두지 않는다.
 */
class GuestPromotionRefreshIntegrationTest extends IntegrationTest {

    private static final int ATTEMPTS = 20;

    @Autowired
    private AuthService authService;

    @Autowired
    private MemberJoinService memberJoinService;

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Test
    void 승격_뒤_게스트_refresh가_다시_와도_소셜_refresh는_유효하다() {
        Member guest = memberJoinService.joinAsGuest();
        String guestRefresh = refreshTokenService.issue(guest);
        LoginResult promoted = loginWithKakao("500001", guest.getId());

        assertThat(refreshTokenService.rotate(guestRefresh)).isEmpty();

        assertThat(refreshTokenService.rotate(promoted.tokens().refreshToken())).isPresent();
    }

    @Test
    void 게스트의_refresh_갱신과_승격이_동시에_와도_데드락_없이_소셜_refresh가_유효하다() throws Exception {
        // 회전은 refresh → 회원(FK 확인) 순서로, 승격은 회원 → refresh 순서로 잠그면 서로를 기다린다.
        // 겹치는 순간이 매번 생기지는 않으므로 여러 번 시도한다.
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
                Member guest = memberJoinService.joinAsGuest();
                String guestRefresh = refreshTokenService.issue(guest);
                String kakaoId = String.valueOf(500_101L + attempt);
                willReturn(new SocialIdentity(kakaoId, "소현")).given(kakaoIdentityAdapter).verify(anyString());

                CountDownLatch start = new CountDownLatch(1);
                Future<Optional<RotatedRefreshToken>> rotated = executor.submit(() -> {
                    start.await();
                    return refreshTokenService.rotate(guestRefresh);
                });
                Future<LoginResult> promoted = executor.submit(() -> {
                    start.await();
                    return authService.loginWithSocial(SocialProvider.KAKAO, "kakao-id-token", guest.getId());
                });
                start.countDown();

                rotated.get(10, TimeUnit.SECONDS);
                String socialRefresh = promoted.get(10, TimeUnit.SECONDS).tokens().refreshToken();
                assertThat(refreshTokenService.rotate(socialRefresh))
                        .as("시도 %d: 승격으로 받은 refresh가 유효해야 한다", attempt + 1)
                        .isPresent();
            }
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void 게스트의_refresh_갱신과_버림이_동시에_와도_데드락_없이_기존_회원의_refresh가_유효하다() throws Exception {
        // 버림은 게스트를 FOR UPDATE로 잠그고 게스트 refresh를 일괄 폐기한 뒤, 기존 회원의 refresh를 INSERT하며
        // 그 회원도 FK 확인으로 공유 잠금한다. 두 회원을 잠그는 유일한 흐름이라 승격과 따로 확인한다.
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
                String kakaoId = String.valueOf(500_201L + attempt);
                loginWithKakao(kakaoId, null);
                Member guest = memberJoinService.joinAsGuest();
                String guestRefresh = refreshTokenService.issue(guest);

                CountDownLatch start = new CountDownLatch(1);
                Future<Optional<RotatedRefreshToken>> rotated = executor.submit(() -> {
                    start.await();
                    return refreshTokenService.rotate(guestRefresh);
                });
                Future<LoginResult> abandoned = executor.submit(() -> {
                    start.await();
                    return authService.loginWithSocial(SocialProvider.KAKAO, "kakao-id-token", guest.getId());
                });
                start.countDown();

                rotated.get(10, TimeUnit.SECONDS);
                LoginResult login = abandoned.get(10, TimeUnit.SECONDS);
                int tried = attempt + 1;
                assertThat(login.guestAbandoned()).as("시도 %d: 게스트를 버려야 한다", tried).isTrue();
                assertThat(refreshTokenService.rotate(login.tokens().refreshToken()))
                        .as("시도 %d: 기존 회원으로 받은 refresh가 유효해야 한다", tried)
                        .isPresent();
            }
        } finally {
            executor.shutdownNow();
        }
    }

    private LoginResult loginWithKakao(String kakaoId, Long guestId) {
        willReturn(new SocialIdentity(kakaoId, "소현")).given(kakaoIdentityAdapter).verify(anyString());
        return authService.loginWithSocial(SocialProvider.KAKAO, "kakao-id-token", guestId);
    }
}
