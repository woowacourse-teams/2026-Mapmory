package com.mapmory.backend.notification.worker;

import com.mapmory.backend.notification.domain.NotificationOutbox;
import com.mapmory.backend.notification.domain.NotificationOutboxRepository;
import jakarta.transaction.Transactional;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class NotificationOutboxClaimer {
    // todo 우선 5분으로 두고, 실제 발송 시간에 맞춰 조정 예정
    private static final long LEASE_MINUTES = 5;

    private final NotificationOutboxRepository outboxRepository;
    private final Clock clock;

    public NotificationOutboxClaimer(
            final NotificationOutboxRepository outboxRepository,
            final Clock clock
    ) {
        this.outboxRepository = outboxRepository;
        this.clock = clock.withZone(ZoneOffset.UTC);
    }

    @Transactional
    public Optional<ClaimedNotification> claimNext() {
        LocalDateTime nowUtc = LocalDateTime.now(clock);

        return outboxRepository.findNextPendingForUpdate(nowUtc)
                .map(outbox -> claim(outbox, nowUtc));
    }

    @Transactional
    public Optional<ClaimedNotification> claimNextForType(final String eventType) {
        LocalDateTime nowUtc = LocalDateTime.now(clock);
        return outboxRepository.findNextPendingForTypeForUpdate(eventType, nowUtc)
                .map(outbox -> claim(outbox, LocalDateTime.now(clock)));
    }

    private ClaimedNotification claim(
            final NotificationOutbox outbox,
            final LocalDateTime nowUtc
    ) {
        String claimToken = UUID.randomUUID().toString();

        // 잠근 상태에서 선점 정보를 기록한다.
        outbox.claim(claimToken, nowUtc.plusMinutes(LEASE_MINUTES));

        return new ClaimedNotification(
                outbox.getId(),
                claimToken,
                outbox.getEventType(),
                outbox.getPayload(),
                outbox.getMemberId(),
                outbox.getPushTokenId()
        );
    }

    @Transactional
    public boolean recoverNextExpired() {
        LocalDateTime nowUtc = LocalDateTime.now(clock);

        Optional<NotificationOutbox> expired =
                outboxRepository.findNextExpiredForUpdate(nowUtc);

        if (expired.isEmpty()) {
            return false;
        }

        expired.get().recoverExpiredClaim(nowUtc);
        return true;
    }

    @Transactional
    public boolean recoverNextExpiredForType(final String eventType) {
        LocalDateTime nowUtc = LocalDateTime.now(clock);
        Optional<NotificationOutbox> expired = outboxRepository.findNextExpiredForTypeForUpdate(eventType, nowUtc);
        if (expired.isEmpty()) {
            return false;
        }
        expired.get().recoverExpiredClaim(LocalDateTime.now(clock));
        return true;
    }
}
