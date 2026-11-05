package com.mapmory.backend.notification.infrastructure;

import com.mapmory.backend.notification.application.InactivePushTokenCleanup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 하루 한 번 탈퇴하거나 버려진 회원의 기기 토큰을 지운다. 알림 스케줄러를 쓰므로 API를 받지 않는 렌더러 서버에서는 돌지 않는다.
 */
@Component
@ConditionalOnProperty(prefix = "notification.outbox", name = "enabled", havingValue = "true", matchIfMissing = true)
class PushTokenCleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(PushTokenCleanupScheduler.class);

    private final InactivePushTokenCleanup cleanup;

    PushTokenCleanupScheduler(InactivePushTokenCleanup cleanup) {
        this.cleanup = cleanup;
    }

    @Scheduled(cron = "0 0 19 * * *", zone = "UTC", scheduler = NotificationConfig.SCHEDULER)
    void cleanUp() {
        try {
            int removed = cleanup.run();
            log.atInfo()
                    .addKeyValue("event", "PUSH_TOKEN_CLEANUP_DONE")
                    .addKeyValue("removed", removed)
                    .log("활동 중이 아닌 회원의 기기 토큰을 지웠다");
        } catch (RuntimeException exception) {
            // 지운 행은 그대로 두고 남은 행은 다음 날 다시 찾는다.
            log.atWarn()
                    .addKeyValue("event", "PUSH_TOKEN_CLEANUP_FAILED")
                    .setCause(exception)
                    .log("활동 중이 아닌 회원의 기기 토큰을 지우지 못했다");
        }
    }
}
