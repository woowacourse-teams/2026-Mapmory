package com.mapmory.backend.travelrecord.infrastructure;

import com.mapmory.backend.common.scheduling.MaintenanceSchedulingConfig;
import com.mapmory.backend.travelrecord.application.OrphanedMediaCleanup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 하루 한 번 기록에서 떨어져 나간 S3 객체를 지운다. API를 받지 않는 렌더러 서버에서는 돌지 않는다.
 */
@Component
@ConditionalOnProperty(prefix = "notification.outbox", name = "enabled", havingValue = "true", matchIfMissing = true)
class OrphanedMediaCleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(OrphanedMediaCleanupScheduler.class);

    private final OrphanedMediaCleanup cleanup;

    OrphanedMediaCleanupScheduler(OrphanedMediaCleanup cleanup) {
        this.cleanup = cleanup;
    }

    @Scheduled(cron = "0 30 19 * * *", zone = "UTC", scheduler = MaintenanceSchedulingConfig.SCHEDULER)
    void cleanUp() {
        try {
            OrphanedMediaCleanup.Result result = cleanup.run();
            log.atInfo()
                    .addKeyValue("event", "ORPHANED_MEDIA_CLEANUP_DONE")
                    .addKeyValue("deleted", result.deleted())
                    .addKeyValue("failed", result.failed())
                    .addKeyValue("remaining", cleanup.remaining())
                    .log("기록에서 떨어져 나간 S3 객체를 지웠다");
        } catch (RuntimeException exception) {
            // 지운 키는 그대로 두고 남은 키는 다음 날 다시 지운다.
            log.atWarn()
                    .addKeyValue("event", "ORPHANED_MEDIA_CLEANUP_FAILED")
                    .setCause(exception)
                    .log("기록에서 떨어져 나간 S3 객체를 지우지 못했다");
        }
    }
}
