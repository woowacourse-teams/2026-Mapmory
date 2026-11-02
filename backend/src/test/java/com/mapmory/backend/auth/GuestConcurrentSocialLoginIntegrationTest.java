package com.mapmory.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.willAnswer;

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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 같은 게스트가 같은 소셜 계정으로 로그인을 연타하는 경우. (#574)
 *
 * 두 요청이 모두 활동 중인 게스트로 읽고 각자 승격하면 겹치는 정도에 따라 둘 중 하나가 된다.
 *   - 거의 동시: 둘 다 승격의 refresh 폐기로 같은 회원의 refresh 인덱스 구간을 잠근 뒤 새 refresh를 넣다가 데드락으로 한쪽이 실패한다.
 *   - 조금 늦게: 늦은 쪽의 폐기가 먼저 끝난 쪽이 방금 발급한 refresh까지 폐기한다. 그 토큰으로 갱신하면 재사용으로 판정된다.
 *
 * loginWithSocial은 트랜잭션 밖에서 불러야 하므로(Propagation.NEVER) 테스트 메서드에 트랜잭션을 두지 않는다.
 * 데이터는 IntegrationTest의 정리 스크립트가 지운다.
 */
class GuestConcurrentSocialLoginIntegrationTest extends IntegrationTest {

    private static final int ATTEMPTS = 24;

    @Autowired
    private AuthService authService;

    @Autowired
    private MemberJoinService memberJoinService;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Test
    void 같은_게스트가_같은_소셜_계정으로_동시에_로그인해도_두_refresh가_모두_유효하다() throws Exception {
        // 소셜 토큰 검증은 트랜잭션 직전이므로, 여기서 두 요청을 맞춰 출발시켜 트랜잭션이 겹치게 한다.
        // 한쪽은 시도마다 0~5ms 늦게 들어가 두 경우를 모두 겪게 한다. 겹치는 순간이 매번 생기지는 않으므로 여러 번 시도한다.
        CyclicBarrier enterTogether = new CyclicBarrier(2);
        AtomicLong lateBy = new AtomicLong();
        willAnswer(invocation -> {
            if (enterTogether.await(5, TimeUnit.SECONDS) == 0) {
                Thread.sleep(lateBy.get());
            }
            return new SocialIdentity(invocation.getArgument(0), "소현");
        }).given(kakaoIdentityAdapter).verify(anyString());

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
                lateBy.set(attempt % 6);
                Member guest = memberJoinService.joinAsGuest();
                String kakaoId = String.valueOf(200_001L + attempt);

                List<LoginResult> results = loginConcurrently(executor, kakaoId, guest.getId());
                int tried = attempt + 1;

                assertThat(results).allSatisfy(result -> {
                    assertThat(result.newMember()).isFalse();
                    assertThat(result.guestAbandoned()).isFalse();
                    assertThat(refreshTokenService.rotate(result.tokens().refreshToken()))
                            .as("시도 %d: 먼저 발급된 refresh가 늦은 승격에 폐기되면 안 된다", tried)
                            .isPresent();
                });
                assertThat(memberRepository.findByProviderAndProviderId(AuthProvider.KAKAO, kakaoId))
                        .get()
                        .extracting(Member::getId)
                        .isEqualTo(guest.getId());
            }
        } finally {
            executor.shutdownNow();
        }
    }

    private List<LoginResult> loginConcurrently(ExecutorService executor, String kakaoId, Long guestId)
            throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        List<Future<LoginResult>> futures = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            futures.add(executor.submit(() -> {
                start.await();
                return authService.loginWithSocial(SocialProvider.KAKAO, kakaoId, guestId);
            }));
        }
        start.countDown();
        List<LoginResult> results = new ArrayList<>();
        for (Future<LoginResult> future : futures) {
            results.add(future.get(10, TimeUnit.SECONDS));
        }
        return results;
    }
}
