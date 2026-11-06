package com.mapmory.backend.auth.infrastructure;

import com.mapmory.backend.auth.token.refresh.ExpiredRefreshTokenCleanup;
import com.mapmory.backend.common.scheduling.MaintenanceSchedulingConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 하루 한 번 만료된 refresh 토큰을 지운다. API를 받지 않는 렌더러 서버에서는 돌지 않는다.
 */
@Component
@ConditionalOnProperty(prefix = "notification.outbox", name = "enabled", havingValue = "true", matchIfMissing = true)
class RefreshTokenCleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenCleanupScheduler.class);

    private final ExpiredRefreshTokenCleanup cleanup;

    RefreshTokenCleanupScheduler(ExpiredRefreshTokenCleanup cleanup) {
        this.cleanup = cleanup;
    }

    @Scheduled(cron = "0 15 19 * * *", zone = "UTC", scheduler = MaintenanceSchedulingConfig.SCHEDULER)
    void cleanUp() {
        try {
            int removed = cleanup.run();
            log.atInfo()
                    .addKeyValue("event", "REFRESH_TOKEN_CLEANUP_DONE")
                    .addKeyValue("removed", removed)
                    .log("만료된 refresh 토큰을 지웠다");
        } catch (RuntimeException exception) {
            // 지운 묶음은 그대로 두고 남은 행은 다음 날 다시 찾는다.
            log.atWarn()
                    .addKeyValue("event", "REFRESH_TOKEN_CLEANUP_FAILED")
                    .setCause(exception)
                    .log("만료된 refresh 토큰을 지우지 못했다");
        }
    }
}
