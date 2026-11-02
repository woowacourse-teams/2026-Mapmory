package com.mapmory.backend.notification.worker;

import com.mapmory.backend.notification.application.port.NotificationPushSender;
import com.mapmory.backend.notification.application.port.PushMessage;
import com.mapmory.backend.notification.domain.PushToken;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "notification.fcm", name = "enabled", havingValue = "true")
public class NotificationOutboxWorker {

    private static final Logger log = LoggerFactory.getLogger(NotificationOutboxWorker.class);
    private static final String EVENT_TYPE = "VIDEO_COMPLETED";

    private final NotificationOutboxClaimer claimer;
    private final NotificationRecipientFinder recipientFinder;
    private final NotificationMessageFactory messageFactory;
    private final NotificationPushSender sender;
    private final NotificationOutboxResultService resultService;

    public NotificationOutboxWorker(
            final NotificationOutboxClaimer claimer,
            final NotificationRecipientFinder recipientFinder,
            final NotificationMessageFactory messageFactory,
            final NotificationPushSender sender,
            final NotificationOutboxResultService resultService
    ) {
        this.claimer = claimer;
        this.recipientFinder = recipientFinder;
        this.messageFactory = messageFactory;
        this.sender = sender;
        this.resultService = resultService;
    }

    @Scheduled(fixedDelayString = "${notification.fcm.poll-delay:3s}", scheduler = "notificationOutboxTaskScheduler")
    public void processNext() {
        try {
            claimer.recoverNextExpiredForType(EVENT_TYPE);
            claimer.claimNextForType(EVENT_TYPE).ifPresent(this::process);
        } catch (RuntimeException exception) {
            // 예외 메시지에 수신 토큰이나 외부 응답이 포함될 수 있어 클래스명만 남긴다.
            log.error("알림 작업 처리 중 오류가 발생했습니다. errorType={}", exception.getClass().getSimpleName());
        }
    }

    private void process(final ClaimedNotification notification) {
        Optional<PushToken> recipient = recipientFinder.find(notification);
        if (recipient.isEmpty()) {
            recordResult(notification, resultService.markSkipped(
                    notification.outboxId(), notification.claimToken(), "RECIPIENT_NOT_FOUND"));
            return;
        }

        PushMessage message;
        try {
            message = messageFactory.create(notification, recipient.get());
        } catch (RuntimeException exception) {
            recordResult(notification, resultService.markFailed(
                    notification.outboxId(), notification.claimToken(), "INVALID_EVENT"));
            return;
        }

        NotificationPushSender.Result result = sender.send(message);
        if (result == null || result.status() == null) {
            recordResult(notification, resultService.markFailed(
                    notification.outboxId(), notification.claimToken(), "INVALID_SENDER_RESULT"));
            return;
        }

        boolean saved = switch (result.status()) {
            case ACCEPTED -> resultService.markSent(notification.outboxId(), notification.claimToken());
            case RETRYABLE_FAILURE, PERMANENT_FAILURE, INVALID_TOKEN -> resultService.markFailed(
                    notification.outboxId(), notification.claimToken(), safeErrorCode(result.errorCode()));
        };
        recordResult(notification, saved);
    }

    private void recordResult(final ClaimedNotification notification, final boolean saved) {
        if (!saved) {
            log.warn("알림 결과를 저장하지 못했습니다. outboxId={}", notification.outboxId());
        }
    }

    private String safeErrorCode(final String errorCode) {
        if (errorCode == null || !errorCode.matches("[A-Z0-9_]{1,100}")) {
            return "FCM_SEND_FAILED";
        }
        return errorCode;
    }
}
