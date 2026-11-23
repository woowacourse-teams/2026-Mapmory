package com.mapmory.backend.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.willReturn;

import com.mapmory.backend.IntegrationTest;
import com.mapmory.backend.auth.application.AuthService;
import com.mapmory.backend.auth.application.model.SocialIdentity;
import com.mapmory.backend.auth.application.model.SocialProvider;
import com.mapmory.backend.common.exception.BusinessException;
import com.mapmory.backend.member.application.MemberJoinService;
import com.mapmory.backend.member.application.MemberService;
import com.mapmory.backend.member.exception.MemberErrorCode;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 닉네임 변경과 탈퇴·승격이 겹치는 경우. (#591)
 *
 * Member는 UPDATE할 때 모든 컬럼을 쓴다. 닉네임 변경이 회원을 잠그지 않고 읽으면, 그사이 커밋된 탈퇴·승격을
 * 처음 읽은 값으로 덮어써 되돌린다.
 *
 * 먼저 시작한 쪽의 트랜잭션을 커밋 직전에 붙들어 겹치는 순간을 매번 만든다. 뒤에 시작한 쪽은 잠금을 기다려 끝나지 않을 수 있으므로,
 * 잠깐만 기다린 뒤 앞쪽을 커밋한다. 테스트 메서드에 트랜잭션을 두지 않는다. 두 요청이 각자 트랜잭션을 연다.
 */
class NicknameChangeConcurrencyIntegrationTest extends IntegrationTest {

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
    private TransactionTemplate transactionTemplate;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    @Test
    void 닉네임_변경_중에_탈퇴해도_탈퇴가_되돌려지지_않는다() throws Exception {
        Long memberId = joinWithKakao("900001");

        Future<?> withdrawal = whileHolding(
                () -> memberService.changeNickname(memberId, "지수"),
                () -> {
                    memberService.withdraw(memberId);
                    return null;
                });
        withdrawal.get(10, TimeUnit.SECONDS);

        Member member = memberRepository.findById(memberId).orElseThrow();
        assertThat(member.isWithdrawn()).isTrue();
        assertThat(member.getProviderId()).isNull();
        assertThat(member.getNickname()).isNotEqualTo("지수");
    }

    @Test
    void 탈퇴_중에_닉네임을_바꾸면_INACTIVE_MEMBER로_거절하고_탈퇴가_되돌려지지_않는다() throws Exception {
        Long memberId = joinWithKakao("900002");

        Future<Member> nicknameChange = whileHolding(
                () -> memberService.withdraw(memberId),
                () -> memberService.changeNickname(memberId, "지수"));

        assertThatThrownBy(() -> nicknameChange.get(10, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(BusinessException.class)
                .cause()
                .extracting("errorCode")
                .isEqualTo(MemberErrorCode.INACTIVE_MEMBER);
        Member member = memberRepository.findById(memberId).orElseThrow();
        assertThat(member.isWithdrawn()).isTrue();
        assertThat(member.getProviderId()).isNull();
    }

    @Test
    void 게스트가_닉네임을_바꾸는_중에_승격해도_승격이_되돌려지지_않고_닉네임은_유지된다() throws Exception {
        Member guest = memberJoinService.joinAsGuest();
        willReturn(new SocialIdentity("900003", "소현")).given(kakaoIdentityAdapter).verify(anyString());

        Future<?> login = whileHolding(
                () -> memberService.changeNickname(guest.getId(), "지수"),
                () -> authService.loginWithSocial(SocialProvider.KAKAO, "kakao-id-token", guest.getId()));
        login.get(10, TimeUnit.SECONDS);

        Member member = memberRepository.findById(guest.getId()).orElseThrow();
        assertThat(member.getProvider()).isEqualTo(AuthProvider.KAKAO);
        assertThat(member.getProviderId()).isEqualTo("900003");
        assertThat(member.getNickname()).isEqualTo("지수");
    }

    /**
     * first를 트랜잭션 안에서 끝내고 커밋하기 전에 second를 다른 스레드에서 시작한다. second가 끝나거나 잠금을 기다리는 동안 커밋한다.
     */
    private <T> Future<T> whileHolding(Runnable first, Callable<T> second) throws Exception {
        return transactionTemplate.execute(status -> {
            first.run();
            Future<T> overlapping = executor.submit(second);
            try {
                overlapping.get(OVERLAP_MILLIS, TimeUnit.MILLISECONDS);
            } catch (TimeoutException | ExecutionException waitingOrFailed) {
                // 잠금을 기다리거나 이미 실패했다. 결과는 호출부가 Future로 확인한다.
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
            return overlapping;
        });
    }

    private Long joinWithKakao(String kakaoId) {
        willReturn(new SocialIdentity(kakaoId, "소현")).given(kakaoIdentityAdapter).verify(anyString());
        authService.loginWithSocial(SocialProvider.KAKAO, "kakao-id-token", null);
        return memberRepository.findByProviderAndProviderId(AuthProvider.KAKAO, kakaoId).orElseThrow().getId();
    }
}
