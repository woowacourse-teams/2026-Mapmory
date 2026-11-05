package com.mapmory.backend.group.infrastructure;

import com.mapmory.backend.common.scheduling.MaintenanceSchedulingConfig;
import com.mapmory.backend.group.application.InactiveMemberGroupCleanup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 하루 한 번 활동 중이 아닌 회원을 그룹에서 뺀다. API를 받는 서버에서만 돈다. 렌더러 서버는 알림 아웃박스와 함께 끈다.
 */
@Component
@ConditionalOnProperty(prefix = "notification.outbox", name = "enabled", havingValue = "true", matchIfMissing = true)
class InactiveMemberGroupCleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(InactiveMemberGroupCleanupScheduler.class);

    private final InactiveMemberGroupCleanup cleanup;

    InactiveMemberGroupCleanupScheduler(InactiveMemberGroupCleanup cleanup) {
        this.cleanup = cleanup;
    }

    @Scheduled(cron = "0 0 19 * * *", zone = "UTC", scheduler = MaintenanceSchedulingConfig.SCHEDULER)
    void cleanUp() {
        try {
            int handled = cleanup.run();
            log.atInfo()
                    .addKeyValue("event", "GROUP_MEMBER_CLEANUP_DONE")
                    .addKeyValue("handled", handled)
                    .log("활동 중이 아닌 회원을 그룹에서 뺐다");
        } catch (RuntimeException exception) {
            // 뺀 회원은 그대로 두고 남은 회원은 다음 날 다시 찾는다.
            log.atWarn()
                    .addKeyValue("event", "GROUP_MEMBER_CLEANUP_FAILED")
                    .setCause(exception)
                    .log("활동 중이 아닌 회원을 그룹에서 빼지 못했다");
        }
    }
}
