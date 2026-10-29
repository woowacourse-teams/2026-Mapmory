package com.mapmory.backend.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mapmory.backend.member.Member;
import com.mapmory.backend.member.MemberRepository;
import com.mapmory.backend.notification.domain.NotificationOutbox;
import com.mapmory.backend.notification.domain.NotificationOutboxRepository;
import com.mapmory.backend.notification.domain.NotificationOutboxStatus;
import com.mapmory.backend.notification.worker.ClaimedNotification;
import com.mapmory.backend.notification.worker.NotificationOutboxClaimer;
import com.mapmory.backend.sharevideo.ShareVideoJob;
import com.mapmory.backend.sharevideo.ShareVideoFailureCode;
import com.mapmory.backend.sharevideo.ShareVideoJobRepository;
import com.mapmory.backend.sharevideo.ShareVideoStatus;
import com.mapmory.backend.sharevideo.ShareVideoStyle;
import com.mapmory.backend.sharevideo.worker.ShareVideoJobUpdater;
import com.mapmory.backend.sharevideo.worker.ShareVideoNotificationOutboxWriter;
import com.mapmory.backend.support.MySqlTestContainerSupport;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

@DataJpaTest
@Import({ShareVideoJobUpdater.class, ShareVideoNotificationOutboxWriter.class,
        NotificationOutboxClaimer.class, NotificationOutboxIntegrationTest.TestBeans.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class NotificationOutboxIntegrationTest extends MySqlTestContainerSupport {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 27, 3, 0);

    @Autowired private ShareVideoJobUpdater jobUpdater;
    @Autowired private ShareVideoJobRepository jobRepository;
    @Autowired private MemberRepository memberRepository;
    @Autowired private NotificationOutboxRepository outboxRepository;
    @Autowired private NotificationOutboxClaimer claimer;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private JsonMapper jsonMapper;

    @TestConfiguration
    static class TestBeans {
        @Bean Clock clock() {
            return Clock.fixed(Instant.parse("2026-10-27T03:00:00Z"), ZoneOffset.UTC);
        }

        @Bean JsonMapper jsonMapper() {
            return JsonMapper.builder().build();
        }
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM notification_outbox");
        jdbcTemplate.update("DELETE FROM share_video_job");
        jdbcTemplate.update("DELETE FROM member WHERE nickname = ?", "아웃박스 테스트");
    }

    @Test
    void 영상_완료와_알림_기록을_함께_저장한다() {
        ShareVideoJob job = saveJob(11L);

        jobUpdater.complete(job.getId(), "share-videos/result.mp4", 100L, 20L);

        assertThat(jobRepository.findById(job.getId()).orElseThrow().getStatus())
                .isEqualTo(ShareVideoStatus.COMPLETED);
        assertThat(outboxRepository.findAll()).singleElement().satisfies(outbox -> {
            assertThat(outbox.getEventType()).isEqualTo("VIDEO_COMPLETED");
            assertThat(outbox.getEventKey()).isEqualTo("video:" + job.getId() + ":completed");
            assertThat(outbox.getMemberId()).isEqualTo(job.getMemberId());
            assertThat(outbox.getPushTokenId()).isEqualTo(11L);
            assertThat(jsonMapper.readTree(outbox.getPayload()).get("jobId").asLong())
                    .isEqualTo(job.getId());
            assertThat(outbox.getStatus()).isEqualTo(NotificationOutboxStatus.PENDING);
        });
    }

    @Test
    void 수신_기기가_없으면_영상만_완료한다() {
        ShareVideoJob job = saveJob(null);

        jobUpdater.complete(job.getId(), "share-videos/result.mp4", 100L, 20L);

        assertThat(jobRepository.findById(job.getId()).orElseThrow().getStatus())
                .isEqualTo(ShareVideoStatus.COMPLETED);
        assertThat(outboxRepository.count()).isZero();
    }

    @Test
    void 같은_결과로_완료_처리를_재시도해도_아웃박스는_한_건이다() {
        ShareVideoJob job = saveJob(11L);

        jobUpdater.complete(job.getId(), "result.mp4", 100L, 20L);
        jobUpdater.complete(job.getId(), "result.mp4", 100L, 20L);

        assertThat(jobRepository.findById(job.getId()).orElseThrow().getStatus())
                .isEqualTo(ShareVideoStatus.COMPLETED);
        assertThat(outboxRepository.count()).isEqualTo(1);
    }

    @Test
    void 완료된_영상에_다른_결과_경로를_저장할_수_없다() {
        ShareVideoJob job = saveJob(11L);
        jobUpdater.complete(job.getId(), "result.mp4", 100L, 20L);

        assertThatThrownBy(() -> jobUpdater.complete(job.getId(), "other.mp4", 100L, 20L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("결과 경로가 다릅니다");

        assertThat(jobRepository.findById(job.getId()).orElseThrow().getResultObjectKey())
                .isEqualTo("result.mp4");
        assertThat(outboxRepository.count()).isEqualTo(1);
    }

    @Test
    void 완료된_작업은_뒤늦은_실패_처리로_변경되지_않는다() {
        ShareVideoJob job = saveJob(11L);
        jobUpdater.complete(job.getId(), "result.mp4", 100L, 20L);

        jobUpdater.fail(job.getId(), ShareVideoFailureCode.WORKER_FAILED,
                new IllegalStateException("늦은 실패"), 100L, 20L);

        ShareVideoJob reloaded = jobRepository.findById(job.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(ShareVideoStatus.COMPLETED);
        assertThat(reloaded.getResultObjectKey()).isEqualTo("result.mp4");
        assertThat(outboxRepository.count()).isEqualTo(1);
    }

    @Test
    void 아웃박스_저장에_실패하면_영상_완료도_롤백한다() {
        ShareVideoJob job = saveJob(11L);
        outboxRepository.saveAndFlush(NotificationOutbox.pending(
                "VIDEO_COMPLETED", "video:" + job.getId() + ":completed",
                "{}", job.getMemberId(), 11L, NOW));

        assertThatThrownBy(() -> jobUpdater.complete(job.getId(), "result.mp4", 100L, 20L))
                .isInstanceOf(DataIntegrityViolationException.class);

        ShareVideoJob reloaded = jobRepository.findById(job.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(ShareVideoStatus.RENDERING);
        assertThat(reloaded.getResultObjectKey()).isNull();
        assertThat(outboxRepository.count()).isEqualTo(1);
    }

    @Test
    void 발송_시각이_된_알림만_선점한다() {
        NotificationOutbox ready = saveOutbox("ready", NOW);
        saveOutbox("future", NOW.plusSeconds(1));

        ClaimedNotification claimed = claimer.claimNext().orElseThrow();

        assertThat(claimed.outboxId()).isEqualTo(ready.getId());
        assertThat(UUID.fromString(claimed.claimToken())).isNotNull();
        assertThat(outboxRepository.findById(ready.getId()).orElseThrow().getStatus())
                .isEqualTo(NotificationOutboxStatus.PROCESSING);
        assertThat(claimer.claimNext()).isEmpty();
    }

    @Test
    void 만료된_선점만_복구하고_다시_선점한다() {
        NotificationOutbox expired = NotificationOutbox.pending("VIDEO_COMPLETED", "expired", "{}", 1L, 1L, NOW);
        expired.claim(UUID.randomUUID().toString(), NOW);
        outboxRepository.saveAndFlush(expired);
        NotificationOutbox active = NotificationOutbox.pending("VIDEO_COMPLETED", "active", "{}", 1L, 1L, NOW);
        active.claim(UUID.randomUUID().toString(), NOW.plusSeconds(1));
        outboxRepository.saveAndFlush(active);

        assertThat(claimer.recoverNextExpired()).isTrue();
        assertThat(claimer.recoverNextExpired()).isFalse();
        assertThat(outboxRepository.findById(expired.getId()).orElseThrow().getStatus())
                .isEqualTo(NotificationOutboxStatus.PENDING);
        assertThat(outboxRepository.findById(active.getId()).orElseThrow().getStatus())
                .isEqualTo(NotificationOutboxStatus.PROCESSING);
        assertThat(claimer.claimNext().orElseThrow().outboxId()).isEqualTo(expired.getId());
    }

    @Test
    void 다른_트랜잭션이_잠근_알림은_중복_선점하지_않는다() throws Exception {
        NotificationOutbox outbox = saveOutbox("locked", NOW);
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<?> lockHolder = executor.submit(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                assertThat(outboxRepository.findNextPendingForUpdate(NOW)).isPresent();
                locked.countDown();
                try {
                    if (!release.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("잠금 해제 대기 시간 초과");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(e);
                }
            }));
            assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();
            assertThat(claimer.claimNext()).isEmpty();
            release.countDown();
            lockHolder.get(10, TimeUnit.SECONDS);
            assertThat(claimer.claimNext().orElseThrow().outboxId()).isEqualTo(outbox.getId());
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    private ShareVideoJob saveJob(Long pushTokenId) {
        Member member = memberRepository.save(Member.of("아웃박스 테스트", UUID.randomUUID()));
        ShareVideoJob job = ShareVideoJob.pending(
                member, "TRAVEL_TIMELINE", ShareVideoStyle.HORIZON, "{}", pushTokenId);
        job.startRendering(NOW);
        return jobRepository.saveAndFlush(job);
    }

    private NotificationOutbox saveOutbox(String key, LocalDateTime nextAttemptAt) {
        return outboxRepository.saveAndFlush(NotificationOutbox.pending(
                "VIDEO_COMPLETED", key, "{}", 1L, 1L, nextAttemptAt));
    }
}
