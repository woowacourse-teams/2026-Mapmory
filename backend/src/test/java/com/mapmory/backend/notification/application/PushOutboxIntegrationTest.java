package com.mapmory.backend.notification.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.mapmory.backend.IntegrationTest;
import com.mapmory.backend.group.api.AlbumUpdated;
import com.mapmory.backend.member.AuthProvider;
import com.mapmory.backend.member.Member;
import com.mapmory.backend.member.MemberRepository;
import com.mapmory.backend.notification.domain.PushOutbox;
import com.mapmory.backend.notification.domain.PushOutboxRepository;
import com.mapmory.backend.notification.domain.PushOutboxStatus;
import com.mapmory.backend.notification.domain.PushOutboxType;
import com.mapmory.backend.notification.domain.PushPlatform;
import com.mapmory.backend.notification.domain.PushToken;
import com.mapmory.backend.notification.domain.PushTokenRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class PushOutboxIntegrationTest extends IntegrationTest {

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private PushOutboxRepository pushOutboxRepository;

    @Autowired
    private PushOutboxRelay pushOutboxRelay;

    @Autowired
    private PushTokenRepository pushTokenRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private NotificationSettingService notificationSettingService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private TransactionTemplate transactionTemplate;
    private Member jisu;
    private Member minho;

    @BeforeEach
    void setUp() {
        transactionTemplate = new TransactionTemplate(transactionManager);
        jisu = member("지수");
        minho = member("민호");
        pushTokenRepository.save(new PushToken(minho.getId(), "minho-phone", PushPlatform.ANDROID));
    }

    @Test
    void 앨범_쓰기가_커밋되면_알림이_적혀_있다() {
        transactionTemplate.executeWithoutResult(status -> eventPublisher.publishEvent(event()));

        assertThat(pushOutboxRepository.findAll())
                .singleElement()
                .satisfies(outbox -> assertThat(outbox.getStatus()).isEqualTo(PushOutboxStatus.PENDING));
    }

    @Test
    void 앨범_쓰기가_롤백되면_알림도_없다() {
        transactionTemplate.executeWithoutResult(status -> {
            eventPublisher.publishEvent(event());
            status.setRollbackOnly();
        });

        assertThat(pushOutboxRepository.count()).isZero();
    }

    @Test
    void 트랜잭션_밖에서_온_이벤트는_적지_않고_막는다() {
        assertThatThrownBy(() -> eventPublisher.publishEvent(event()))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    void 보낼_차례인_알림을_보내고_보냄으로_표시한다() {
        write(event());

        assertThat(pushOutboxRelay.relayDue()).isEqualTo(1);

        verify(pushSender).send(anyList());
        assertThat(onlyOutbox().getStatus()).isEqualTo(PushOutboxStatus.SENT);
        assertThat(pushOutboxRelay.relayDue()).isZero();
    }

    @Test
    void 적힌_뒤에_알림을_끄면_보내지_않고_보냄으로_표시한다() {
        write(event());
        notificationSettingService.changeGroupAlbum(minho.getId(), false);

        assertThat(pushOutboxRelay.relayDue()).isEqualTo(1);

        verify(pushSender, never()).send(anyList());
        assertThat(onlyOutbox().getStatus()).isEqualTo(PushOutboxStatus.SENT);
    }

    @Test
    void 모르는_필드가_있는_알림도_읽어_보낸다() {
        String payload = """
                {"groupId":3,"groupName":"제주 가족","albumId":7,"albumName":"도쿄 3월",
                 "actorMemberId":%d,"recipientMemberIds":[%d],"recordId":99}
                """.formatted(jisu.getId(), minho.getId());
        pushOutboxRepository.save(PushOutbox.pending(PushOutboxType.ALBUM_UPDATED, payload, LocalDateTime.now(clock)));

        pushOutboxRelay.relayDue();

        verify(pushSender).send(anyList());
        assertThat(onlyOutbox().getStatus()).isEqualTo(PushOutboxStatus.SENT);
    }

    @Test
    void 실패하면_다음_시도를_미루고_그_전에는_다시_집지_않는다() {
        willThrow(new IllegalStateException("fcm down")).given(pushSender).send(anyList());
        write(event());

        pushOutboxRelay.relayDue();

        PushOutbox outbox = onlyOutbox();
        assertThat(outbox.getStatus()).isEqualTo(PushOutboxStatus.PENDING);
        assertThat(outbox.getAttempts()).isEqualTo(1);
        assertThat(outbox.getLastError()).contains("fcm down");
        assertThat(outbox.getNextAttemptAt()).isAfter(LocalDateTime.now(clock));
        assertThat(pushOutboxRelay.relayDue()).isZero();
    }

    @Test
    void 다섯_번_실패하면_포기한다() {
        willThrow(new IllegalStateException("fcm down")).given(pushSender).send(anyList());
        write(event());

        for (int i = 0; i < 5; i++) {
            makeDueNow();
            pushOutboxRelay.relayDue();
        }

        assertThat(onlyOutbox().getStatus()).isEqualTo(PushOutboxStatus.FAILED);
        makeDueNow();
        assertThat(pushOutboxRelay.relayDue()).isZero();
        verify(pushSender, times(5)).send(anyList());
    }

    @Test
    void 두_서버가_함께_비워도_알림마다_한_번씩만_보낸다() throws Exception {
        IntStream.range(0, 30).forEach(i -> write(event()));
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<CompletableFuture<Integer>> runs = IntStream.range(0, 2)
                    .mapToObj(i -> CompletableFuture.supplyAsync(() -> {
                        await(start);
                        int total = 0;
                        int relayed;
                        while ((relayed = pushOutboxRelay.relayDue()) > 0) {
                            total += relayed;
                        }
                        return total;
                    }, pool))
                    .toList();
            start.countDown();

            int total = runs.stream().mapToInt(CompletableFuture::join).sum();

            assertThat(total).isEqualTo(30);
            verify(pushSender, times(30)).send(anyList());
            assertThat(pushOutboxRepository.findAll())
                    .allSatisfy(outbox -> assertThat(outbox.getStatus()).isEqualTo(PushOutboxStatus.SENT));
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void 끝난_지_7일_지난_알림만_지운다() {
        write(event());
        write(event());
        write(event());
        List<Long> ids = pushOutboxRepository.findAll().stream().map(PushOutbox::getId).sorted().toList();
        jdbcTemplate.update("UPDATE push_outbox SET status = 'SENT', updated_at = ? WHERE id = ?",
                LocalDateTime.now(clock).minusDays(8), ids.get(0));
        jdbcTemplate.update("UPDATE push_outbox SET status = 'SENT' WHERE id = ?", ids.get(1));
        jdbcTemplate.update("UPDATE push_outbox SET updated_at = ? WHERE id = ?",
                LocalDateTime.now(clock).minusDays(8), ids.get(2));

        assertThat(pushOutboxRelay.cleanUp()).isEqualTo(1);

        assertThat(pushOutboxRepository.findAll()).extracting(PushOutbox::getId)
                .containsExactlyInAnyOrder(ids.get(1), ids.get(2));
    }

    private AlbumUpdated event() {
        return new AlbumUpdated(3L, "제주 가족", 7L, "도쿄 3월", jisu.getId(), List.of(minho.getId()));
    }

    private void write(AlbumUpdated event) {
        transactionTemplate.executeWithoutResult(status -> eventPublisher.publishEvent(event));
    }

    private PushOutbox onlyOutbox() {
        List<PushOutbox> all = pushOutboxRepository.findAll();
        assertThat(all).hasSize(1);
        return all.getFirst();
    }

    private void makeDueNow() {
        jdbcTemplate.update("UPDATE push_outbox SET next_attempt_at = ?", LocalDateTime.now(clock).minusSeconds(1));
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }

    private Member member(String nickname) {
        Member member = Member.ofOAuth(AuthProvider.KAKAO, UUID.randomUUID().toString(), nickname, UUID.randomUUID());
        member.changeNickname(nickname);
        return memberRepository.save(member);
    }
}
