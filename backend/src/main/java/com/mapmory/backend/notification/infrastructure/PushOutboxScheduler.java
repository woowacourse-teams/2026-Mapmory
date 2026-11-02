package com.mapmory.backend.notification.infrastructure;

import com.mapmory.backend.notification.application.PushOutboxRelay;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 아웃박스를 주기적으로 비운다. 서버가 여러 대여도 SKIP LOCKED로 같은 행을 함께 집지 않는다.
 */
@Component
@ConditionalOnProperty(prefix = "notification.outbox", name = "enabled", havingValue = "true", matchIfMissing = true)
class PushOutboxScheduler {

    private static final Logger log = LoggerFactory.getLogger(PushOutboxScheduler.class);

    private final PushOutboxRelay pushOutboxRelay;

    PushOutboxScheduler(PushOutboxRelay pushOutboxRelay) {
        this.pushOutboxRelay = pushOutboxRelay;
    }

    // 앞 차례가 끝난 뒤 이만큼 쉬고 다시 본다. 차례마다 최대 20개를 보낸다.
    @Scheduled(fixedDelayString = "${notification.outbox.poll-delay:2s}", scheduler = NotificationConfig.SCHEDULER)
    void relay() {
        try {
            pushOutboxRelay.relayDue();
        } catch (RuntimeException exception) {
            // DB가 잠깐 끊겨도 다음 차례에 다시 본다. 집은 행은 잠금(5분)이 지나면 다시 집힌다.
            log.atWarn()
                    .addKeyValue("event", "PUSH_OUTBOX_RELAY_FAILED")
                    .setCause(exception)
                    .log("알림 아웃박스를 비우지 못했다");
        }
    }

    @Scheduled(cron = "0 40 4 * * *", zone = "UTC", scheduler = NotificationConfig.SCHEDULER)
    void cleanUp() {
        pushOutboxRelay.cleanUp();
    }
}
