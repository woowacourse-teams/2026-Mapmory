package com.mapmory.backend.travelrecord.infrastructure;

import com.mapmory.backend.common.scheduling.MaintenanceSchedulingConfig;
import com.mapmory.backend.travelrecord.application.UnlinkedMediaReconciliation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 하루 한 번 기록에 붙지 않은 S3 객체를 찾아 정리 큐에 넣는다. API를 받지 않는 렌더러 서버에서는 돌지 않는다.
 *
 * <p>19:30 UTC의 큐 정리(#162) 뒤에 돈다. 여기서 넣은 키는 다음 날 큐 정리가 다시 확인하고 지우므로, 그사이 기록에 붙은 키는 살아남는다.
 */
@Component
@ConditionalOnProperty(prefix = "notification.outbox", name = "enabled", havingValue = "true", matchIfMissing = true)
class UnlinkedMediaReconciliationScheduler {

    private static final Logger log = LoggerFactory.getLogger(UnlinkedMediaReconciliationScheduler.class);

    private final UnlinkedMediaReconciliation reconciliation;

    UnlinkedMediaReconciliationScheduler(UnlinkedMediaReconciliation reconciliation) {
        this.reconciliation = reconciliation;
    }

    @Scheduled(cron = "0 0 20 * * *", zone = "UTC", scheduler = MaintenanceSchedulingConfig.SCHEDULER)
    void reconcile() {
        try {
            UnlinkedMediaReconciliation.Result result = reconciliation.run();
            log.atInfo()
                    .addKeyValue("event", "UNLINKED_MEDIA_RECONCILE_DONE")
                    .addKeyValue("dryRun", result.dryRun())
                    .addKeyValue("scanned", result.scanned())
                    .addKeyValue("candidates", result.candidates())
                    .addKeyValue("queued", result.queued())
                    .addKeyValue("failedPages", result.failedPages())
                    .addKeyValue("sample", result.sample())
                    .log("기록에 붙지 않은 S3 객체를 대조했다");
        } catch (RuntimeException exception) {
            // 목록 조회가 실패하면 다음 쪽을 알 수 없다. 넣은 키는 그대로 두고 다음 날 처음부터 다시 읽는다.
            log.atWarn()
                    .addKeyValue("event", "UNLINKED_MEDIA_RECONCILE_FAILED")
                    .setCause(exception)
                    .log("기록에 붙지 않은 S3 객체를 대조하지 못했다");
        }
    }
}
