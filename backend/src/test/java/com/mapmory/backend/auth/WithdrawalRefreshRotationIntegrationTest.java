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
import com.mapmory.backend.member.AuthProvider;
import com.mapmory.backend.member.Member;
import com.mapmory.backend.member.MemberRepository;
import com.mapmory.backend.member.application.MemberService;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 탈퇴와 refresh 회전이 동시에 오는 경우. (#579, ADR 0028)
 *
 * 회전은 회원을 공유 잠금한 뒤 refresh를 잠근다. 탈퇴가 회원을 잠그지 않으면 refresh 일괄 폐기가 먼저,
 * 회원 UPDATE가 커밋 때 나가 순서가 반대가 되어 데드락이 난다. 탈퇴도 회원을 먼저 잠가 순서를 맞춘다.
 *
 * 테스트 메서드에 트랜잭션을 두지 않는다. 두 요청이 각자 트랜잭션을 연다.
 */
class WithdrawalRefreshRotationIntegrationTest extends IntegrationTest {

    private static final int ATTEMPTS = 20;

    @Autowired
    private AuthService authService;

    @Autowired
    private MemberService memberService;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Test
    void 탈퇴와_refresh_갱신이_동시에_와도_데드락_없이_탈퇴되고_쓸_수_있는_refresh가_남지_않는다() throws Exception {
        // 겹치는 순간이 매번 생기지는 않으므로 여러 번 시도한다.
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
                String kakaoId = String.valueOf(600_001L + attempt);
                willReturn(new SocialIdentity(kakaoId, "소현")).given(kakaoIdentityAdapter).verify(anyString());
                LoginResult login = authService.loginWithSocial(SocialProvider.KAKAO, "kakao-id-token", null);
                Long memberId = memberIdOf(kakaoId);

                CountDownLatch start = new CountDownLatch(1);
                Future<Optional<RotatedRefreshToken>> rotated = executor.submit(() -> {
                    start.await();
                    return refreshTokenService.rotate(login.tokens().refreshToken());
                });
                Future<?> withdrawn = executor.submit(() -> {
                    start.await();
                    memberService.withdraw(memberId);
                    return null;
                });
                start.countDown();

                Optional<RotatedRefreshToken> successor = rotated.get(10, TimeUnit.SECONDS);
                withdrawn.get(10, TimeUnit.SECONDS);
                int tried = attempt + 1;
                assertThat(memberRepository.findById(memberId)).get()
                        .as("시도 %d: 탈퇴돼야 한다", tried)
                        .extracting(Member::isWithdrawn)
                        .isEqualTo(true);
                successor.ifPresent(token -> assertThat(refreshTokenService.rotate(token.refreshToken()))
                        .as("시도 %d: 회전으로 받은 refresh도 탈퇴로 폐기돼야 한다", tried)
                        .isEmpty());
            }
        } finally {
            executor.shutdownNow();
        }
    }

    private Long memberIdOf(String kakaoId) {
        return memberRepository.findByProviderAndProviderId(AuthProvider.KAKAO, kakaoId).get().getId();
    }
}
