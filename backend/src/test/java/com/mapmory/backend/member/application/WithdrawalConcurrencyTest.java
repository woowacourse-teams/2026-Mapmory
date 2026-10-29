package com.mapmory.backend.member.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mapmory.backend.IntegrationTest;
import com.mapmory.backend.common.exception.BusinessException;
import com.mapmory.backend.member.AuthProvider;
import com.mapmory.backend.member.Member;
import com.mapmory.backend.member.MemberRepository;
import com.mapmory.backend.notification.application.PushTokenService;
import com.mapmory.backend.notification.domain.PushPlatform;
import com.mapmory.backend.notification.domain.PushTokenRepository;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 활동 중인지 확인한 요청이 커밋하기 전에 탈퇴가 끼어들어도, 탈퇴 정리가 그 요청이 만든 데이터를 놓치지 않는다.
 *
 * <p>한 트랜잭션에서 가드를 먼저 부르고 멈춘 뒤 실제 작업을 이어 한다. 가드를 통과한 직후 멈춘 요청과 같다.
 * 탈퇴는 다른 스레드에서 한다. 가드가 회원 행을 잠그면 탈퇴는 요청이 커밋될 때까지 기다린다.
 */
class WithdrawalConcurrencyTest extends IntegrationTest {

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private MemberService memberService;

    @Autowired
    private ActiveMemberGuard activeMemberGuard;

    @Autowired
    private PushTokenService pushTokenService;

    @Autowired
    private PushTokenRepository pushTokenRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private final ExecutorService executor = Executors.newFixedThreadPool(2);

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    @Test
    void 가드를_통과한_토큰_등록이_탈퇴보다_늦게_커밋돼도_토큰이_남지_않는다() throws Exception {
        Member member = socialMember();

        runAcrossWithdrawal(member.getId(),
                () -> pushTokenService.register(member.getId(), "late-token", PushPlatform.ANDROID));

        assertThat(memberRepository.findById(member.getId()).orElseThrow().isWithdrawn()).isTrue();
        assertThat(pushTokenRepository.findByToken("late-token")).isEmpty();
    }

    @Test
    void 탈퇴가_먼저_커밋되면_가드가_막는다() {
        Member member = socialMember();
        memberService.withdraw(member.getId());

        assertThatThrownBy(() -> pushTokenService.register(member.getId(), "token", PushPlatform.ANDROID))
                .isInstanceOf(BusinessException.class);
    }

    /**
     * 요청이 가드를 통과한 뒤 멈춘 사이 탈퇴를 시작한다. 탈퇴가 잠금에 막히지 않으면 먼저 끝난다.
     * 그다음 요청을 이어 커밋하고 둘 다 끝나기를 기다린다.
     */
    protected void runAcrossWithdrawal(Long memberId, Runnable operation) throws Exception {
        CountDownLatch guarded = new CountDownLatch(1);
        CountDownLatch resume = new CountDownLatch(1);
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);

        Future<?> request = executor.submit(() -> transaction.executeWithoutResult(status -> {
            activeMemberGuard.require(memberId);
            guarded.countDown();
            await(resume);
            operation.run();
        }));
        assertThat(guarded.await(10, TimeUnit.SECONDS)).isTrue();

        Future<?> withdrawal = executor.submit(() -> memberService.withdraw(memberId));
        try {
            withdrawal.get(1, TimeUnit.SECONDS);
        } catch (TimeoutException blockedByRequest) {
            // 가드가 회원 행을 잠갔다. 요청이 커밋되면 탈퇴가 이어진다.
        }
        resume.countDown();

        request.get(15, TimeUnit.SECONDS);
        withdrawal.get(15, TimeUnit.SECONDS);
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(15, TimeUnit.SECONDS)) {
                throw new IllegalStateException("요청을 이어 갈 신호가 오지 않았다");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }

    private Member socialMember() {
        return memberRepository.save(
                Member.ofOAuth(AuthProvider.KAKAO, UUID.randomUUID().toString(), "회원", UUID.randomUUID()));
    }
}
