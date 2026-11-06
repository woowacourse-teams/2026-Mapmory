package com.mapmory.backend.travelrecord.infrastructure;

import com.mapmory.backend.common.scheduling.MaintenanceSchedulingConfig;
import com.mapmory.backend.travelrecord.application.UnlinkedMediaCleanup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 하루 한 번 기록에 붙지 않은 S3 객체를 지운다. API를 받지 않는 렌더러 서버에서는 돌지 않는다.
 *
 * <p>19:30 UTC의 큐 정리(#615)와 회원 파기(#601) 뒤에 돈다. 큐가 먼저 비워져야 이 배치가 건너뛸 키가 줄어든다.
 */
@Component
@ConditionalOnProperty(prefix = "notification.outbox", name = "enabled", havingValue = "true", matchIfMissing = true)
class UnlinkedMediaCleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(UnlinkedMediaCleanupScheduler.class);

    private final UnlinkedMediaCleanup cleanup;

    UnlinkedMediaCleanupScheduler(UnlinkedMediaCleanup cleanup) {
        this.cleanup = cleanup;
    }

    @Scheduled(cron = "0 0 20 * * *", zone = "UTC", scheduler = MaintenanceSchedulingConfig.SCHEDULER)
    void cleanUp() {
        try {
            UnlinkedMediaCleanup.Result result = cleanup.run();
            log.atInfo()
                    .addKeyValue("event", "UNLINKED_MEDIA_CLEANUP_DONE")
                    .addKeyValue("dryRun", result.dryRun())
                    .addKeyValue("scanned", result.scanned())
                    .addKeyValue("candidates", result.candidates())
                    .addKeyValue("deleted", result.deleted())
                    .addKeyValue("failed", result.failed())
                    .addKeyValue("failedPages", result.failedPages())
                    .addKeyValue("sample", result.sample())
                    .log("기록에 붙지 않은 S3 객체를 정리했다");
        } catch (RuntimeException exception) {
            // 목록 조회가 실패하면 다음 쪽을 알 수 없다. 지운 것은 그대로 두고 다음 날 처음부터 다시 읽는다.
            log.atWarn()
                    .addKeyValue("event", "UNLINKED_MEDIA_CLEANUP_FAILED")
                    .setCause(exception)
                    .log("기록에 붙지 않은 S3 객체를 정리하지 못했다");
        }
    }
}
