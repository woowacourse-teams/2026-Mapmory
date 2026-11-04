package com.mapmory.backend.notification.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.willReturn;

import com.mapmory.backend.IntegrationTest;
import com.mapmory.backend.auth.application.AuthService;
import com.mapmory.backend.auth.application.model.SocialIdentity;
import com.mapmory.backend.auth.application.model.SocialProvider;
import com.mapmory.backend.member.AuthProvider;
import com.mapmory.backend.member.Member;
import com.mapmory.backend.member.MemberRepository;
import com.mapmory.backend.member.application.MemberJoinService;
import com.mapmory.backend.member.application.MemberService;
import com.mapmory.backend.notification.domain.PushPlatform;
import com.mapmory.backend.notification.domain.PushToken;
import com.mapmory.backend.notification.domain.PushTokenRepository;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 탈퇴하거나 버려진 회원의 기기 토큰 정리. (#546)
 *
 * 테스트 메서드에 트랜잭션을 두지 않는다. 정리는 탈퇴·로그인이 커밋된 뒤에 돈다.
 */
class PushTokenCleanupIntegrationTest extends IntegrationTest {

    private static final long OVERLAP_MILLIS = 500;

    @Autowired
    private MemberService memberService;

    @Autowired
    private MemberJoinService memberJoinService;

    @Autowired
    private AuthService authService;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private PushTokenRepository pushTokenRepository;

    @Autowired
    private PushTokenService pushTokenService;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final ExecutorService executor = Executors.newFixedThreadPool(2);

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    @Test
    void 탈퇴하면_그_회원의_기기_토큰만_지운다() {
        Long jisu = joinWithKakao("910001");
        Long minho = joinWithKakao("910002");
        save(jisu, "jisu-phone");
        save(jisu, "jisu-tablet");
        save(minho, "minho-phone");

        memberService.withdraw(jisu);

        assertThat(tokensOf(jisu)).isEmpty();
        assertThat(tokensOf(minho)).containsExactly("minho-phone");
    }

    @Test
    void 게스트가_버려지면_그_게스트의_기기_토큰만_지운다() {
        Long registered = joinWithKakao("910003");
        Member guest = memberJoinService.joinAsGuest();
        save(registered, "registered-phone");
        save(guest.getId(), "guest-phone");

        authService.loginWithSocial(SocialProvider.KAKAO, "kakao-id-token", guest.getId());

        assertThat(memberRepository.findById(guest.getId()).orElseThrow().isAbandoned()).isTrue();
        assertThat(tokensOf(guest.getId())).isEmpty();
        assertThat(tokensOf(registered)).containsExactly("registered-phone");
    }

    /**
     * 등록은 회원을 확인한 뒤 토큰 행을 잠근다. 토큰 행을 쥐고 있으면 등록이 확인을 지난 채로 멈추므로, 그사이 탈퇴를 시작한다.
     * 탈퇴와 정리가 먼저 끝나면 그 뒤 커밋된 등록이 탈퇴한 회원의 토큰으로 남는다.
     */
    @Test
    void 확인을_통과한_등록이_탈퇴보다_늦게_끝나도_기기_토큰이_남지_않는다() throws Exception {
        Long jisu = joinWithKakao("910004");
        Long minho = joinWithKakao("910005");
        save(minho, "shared-phone");

        Future<?>[] requests = new Future<?>[2];
        transactionTemplate.executeWithoutResult(status -> {
            jdbcTemplate.queryForList("select id from push_token where token = 'shared-phone' for update");
            requests[0] = executor.submit(
                    () -> pushTokenService.register(jisu, "shared-phone", PushPlatform.ANDROID));
            pause();
            requests[1] = executor.submit(() -> memberService.withdraw(jisu));
            pause();
        });

        requests[0].get(10, TimeUnit.SECONDS);
        requests[1].get(10, TimeUnit.SECONDS);

        assertThat(memberRepository.findById(jisu).orElseThrow().isWithdrawn()).isTrue();
        assertThat(tokensOf(jisu)).isEmpty();
    }

    private void pause() {
        try {
            Thread.sleep(OVERLAP_MILLIS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private Long joinWithKakao(String kakaoId) {
        willReturn(new SocialIdentity(kakaoId, "소현")).given(kakaoIdentityAdapter).verify(anyString());
        authService.loginWithSocial(SocialProvider.KAKAO, "kakao-id-token", null);
        return memberRepository.findByProviderAndProviderId(AuthProvider.KAKAO, kakaoId).orElseThrow().getId();
    }

    private void save(Long memberId, String token) {
        pushTokenRepository.save(new PushToken(memberId, token, PushPlatform.ANDROID));
    }

    private List<String> tokensOf(Long memberId) {
        return pushTokenRepository.findAllByMemberIdIn(List.of(memberId)).stream()
                .map(PushToken::getToken)
                .toList();
    }
}
