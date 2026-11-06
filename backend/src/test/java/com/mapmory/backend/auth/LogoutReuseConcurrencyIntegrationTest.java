package com.mapmory.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.willReturn;

import com.mapmory.backend.IntegrationTest;
import com.mapmory.backend.auth.application.AuthService;
import com.mapmory.backend.auth.application.model.SocialIdentity;
import com.mapmory.backend.auth.application.model.SocialProvider;
import com.mapmory.backend.auth.token.refresh.RefreshTokenService;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 같은 세션에서 로그아웃과 재사용 판정이 동시에 오는 경우. (#467, ADR 0028)
 *
 * 체인 P → R → S에서 로그아웃(P)과 재사용(R)이 겹친다. 세션 행을 먼저 잠그지 않으면 로그아웃은 P를 쥐고 R을,
 * 재사용의 일괄 폐기는 R을 쥐고 이미 폐기된 P를 기다려 데드락이 났다(동시 출발 300회 중 1~4회).
 * 세션 행에서 직렬화되므로 데드락 없이 둘 다 끝나고, 세션에 쓸 수 있는 refresh가 남지 않아야 한다.
 *
 * 수정 전에도 데드락은 드물어 이 테스트가 매번 실패하지는 않는다. 회귀를 막는 안전망으로 둔다.
 * 테스트 메서드에 트랜잭션을 두지 않는다. 두 요청이 각자 트랜잭션을 연다.
 */
class LogoutReuseConcurrencyIntegrationTest extends IntegrationTest {

    private static final int ATTEMPTS = 100;

    @Autowired
    private AuthService authService;

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Test
    void 같은_세션의_로그아웃과_재사용_판정이_겹쳐도_데드락_없이_세션의_refresh가_모두_폐기된다() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
                willReturn(new SocialIdentity(String.valueOf(910_001L + attempt), "소현"))
                        .given(kakaoIdentityAdapter).verify(anyString());
                String first = authService.loginWithSocial(SocialProvider.KAKAO, "kakao-id-token", null)
                        .tokens().refreshToken();
                String reused = refreshTokenService.rotate(first).orElseThrow().refreshToken();
                String latest = refreshTokenService.rotate(reused).orElseThrow().refreshToken();

                CountDownLatch start = new CountDownLatch(1);
                Future<?> logout = executor.submit(() -> {
                    start.await();
                    refreshTokenService.revoke(first);
                    return null;
                });
                Future<?> reuse = executor.submit(() -> {
                    start.await();
                    return refreshTokenService.rotate(reused);
                });
                start.countDown();

                logout.get(10, TimeUnit.SECONDS);
                reuse.get(10, TimeUnit.SECONDS);
                assertThat(refreshTokenService.rotate(latest))
                        .as("시도 %d: 세션의 최신 refresh가 남으면 안 된다", attempt + 1)
                        .isEmpty();
            }
        } finally {
            executor.shutdownNow();
        }
    }
}
