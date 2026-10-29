package com.mapmory.backend.notification.application;

import com.mapmory.backend.group.api.AlbumUpdated;
import com.mapmory.backend.notification.domain.PushOutbox;
import com.mapmory.backend.notification.domain.PushOutboxRepository;
import com.mapmory.backend.notification.domain.PushOutboxType;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

/**
 * 아웃박스에서 보낼 차례인 알림을 꺼내 보낸다(ADR 0022 2026-10-29 보완: 아웃박스).
 *
 * <pre>
 * ① 짧은 트랜잭션: 차례인 행을 SKIP LOCKED로 집고 다음 시도를 5분 뒤로 미룸(처리 중 잠금) → 커밋
 * ② 트랜잭션 밖: 보내기 (FCM을 기다리는 동안 커넥션을 쥐지 않는다)
 * ③ 짧은 트랜잭션: 보냄 / 실패(다시 시도 또는 포기)로 표시
 * </pre>
 *
 * <p>스케줄은 {@code PushOutboxScheduler}가 건다. 테스트는 이 클래스를 직접 부른다.
 */
@Component
public class PushOutboxRelay {

    static final int BATCH_SIZE = 20;
    static final Duration RETENTION = Duration.ofDays(7);
    private static final Logger log = LoggerFactory.getLogger(PushOutboxRelay.class);

    private final PushOutboxRepository pushOutboxRepository;
    private final AlbumPushDelivery albumPushDelivery;
    private final JsonMapper jsonMapper;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public PushOutboxRelay(PushOutboxRepository pushOutboxRepository, AlbumPushDelivery albumPushDelivery,
                           JsonMapper jsonMapper, PlatformTransactionManager transactionManager, Clock clock) {
        this.pushOutboxRepository = pushOutboxRepository;
        this.albumPushDelivery = albumPushDelivery;
        this.jsonMapper = jsonMapper;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    /**
     * @return 이번에 집어 보낸(또는 실패로 표시한) 알림 수
     */
    public int relayDue() {
        List<Claimed> claimed = claim();
        claimed.forEach(this::relay);
        return claimed.size();
    }

    /**
     * 끝난 지 7일 지난 알림을 지운다. 대기 중인 알림은 남긴다.
     */
    public int cleanUp() {
        LocalDateTime before = now().minus(RETENTION);
        return transactionTemplate.execute(status -> pushOutboxRepository.deleteFinishedBefore(before));
    }

    private List<Claimed> claim() {
        LocalDateTime now = now();
        return transactionTemplate.execute(status -> pushOutboxRepository.findDueForUpdate(now, BATCH_SIZE).stream()
                .map(outbox -> {
                    outbox.lease(now);
                    return new Claimed(outbox.getId(), outbox.getType(), outbox.getPayload());
                })
                .toList());
    }

    private void relay(Claimed claimed) {
        try {
            deliver(claimed);
        } catch (RuntimeException exception) {
            markFailed(claimed.id(), exception);
            return;
        }
        transactionTemplate.executeWithoutResult(status ->
                pushOutboxRepository.findById(claimed.id()).ifPresent(PushOutbox::sent));
    }

    private void deliver(Claimed claimed) {
        switch (claimed.type()) {
            case ALBUM_UPDATED -> albumPushDelivery.deliver(jsonMapper.readValue(claimed.payload(), AlbumUpdated.class));
        }
    }

    // 표시마저 실패하면 행은 PENDING으로 남고 잠금(5분)이 지나 다시 집힌다.
    private void markFailed(Long id, RuntimeException exception) {
        transactionTemplate.executeWithoutResult(status -> pushOutboxRepository.findById(id).ifPresent(outbox -> {
            outbox.failed(now(), exception.toString());
            if (outbox.isGivenUp()) {
                log.atError()
                        .addKeyValue("event", "PUSH_GIVEN_UP")
                        .addKeyValue("outboxId", id)
                        .addKeyValue("attempts", outbox.getAttempts())
                        .setCause(exception)
                        .log("알림을 끝내 보내지 못해 포기했다");
                return;
            }
            log.atWarn()
                    .addKeyValue("event", "PUSH_RETRY_SCHEDULED")
                    .addKeyValue("outboxId", id)
                    .addKeyValue("attempts", outbox.getAttempts())
                    .addKeyValue("nextAttemptAt", outbox.getNextAttemptAt())
                    .setCause(exception)
                    .log("알림을 보내지 못해 다시 보낸다");
        }));
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    private record Claimed(Long id, PushOutboxType type, String payload) {
    }
}
