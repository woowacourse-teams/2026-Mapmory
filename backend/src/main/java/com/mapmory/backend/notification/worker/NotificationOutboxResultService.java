package com.mapmory.backend.notification.worker;

import com.mapmory.backend.notification.domain.NotificationOutboxRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationOutboxResultService {

    private final NotificationOutboxRepository outboxRepository;
    private final Clock clock;

    public NotificationOutboxResultService(
            final NotificationOutboxRepository outboxRepository,
            final Clock clock
    ) {
        this.outboxRepository = outboxRepository;
        this.clock = clock.withZone(ZoneOffset.UTC);
    }

    @Transactional
    public boolean markSent(final Long outboxId, final String claimToken) {
        return outboxRepository.findByIdForUpdate(outboxId)
                .map(outbox -> outbox.markSent(claimToken, LocalDateTime.now(clock)))
                .orElse(false);
    }

    @Transactional
    public boolean scheduleRetry(
            final Long outboxId,
            final String claimToken,
            final String errorCode,
            final LocalDateTime nextAttemptAt
    ) {
        return outboxRepository.findByIdForUpdate(outboxId)
                .map(outbox -> outbox.scheduleRetry(claimToken, errorCode, LocalDateTime.now(clock), nextAttemptAt))
                .orElse(false);
    }

    @Transactional
    public boolean markFailed(final Long outboxId, final String claimToken, final String errorCode) {
        return outboxRepository.findByIdForUpdate(outboxId)
                .map(outbox -> outbox.markFailed(claimToken, errorCode, LocalDateTime.now(clock)))
                .orElse(false);
    }

    @Transactional
    public boolean markSkipped(final Long outboxId, final String claimToken, final String reasonCode) {
        return outboxRepository.findByIdForUpdate(outboxId)
                .map(outbox -> outbox.markSkipped(claimToken, reasonCode, LocalDateTime.now(clock)))
                .orElse(false);
    }
}
