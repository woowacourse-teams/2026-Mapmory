package com.mapmory.backend.member.infrastructure;

import com.mapmory.backend.member.application.MemberPurgeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * 하루 한 번(19:30 UTC, 한국 새벽 4시 30분) 회원 데이터를 파기한다. API를 받는 서버에서만 돈다. 렌더러 서버는 알림 아웃박스와 함께 끈다.
 *
 * <p>S3 삭제가 오래 걸릴 수 있어 스레드를 따로 둔다. 알림 스케줄러를 같이 쓰면 그동안 알림이 나가지 않는다.
 */
@Configuration
@ConditionalOnProperty(prefix = "notification.outbox", name = "enabled", havingValue = "true", matchIfMissing = true)
class MemberPurgeScheduler {

    private static final String SCHEDULER = "memberPurgeTaskScheduler";
    private static final Logger log = LoggerFactory.getLogger(MemberPurgeScheduler.class);

    private final MemberPurgeService memberPurgeService;

    MemberPurgeScheduler(MemberPurgeService memberPurgeService) {
        this.memberPurgeService = memberPurgeService;
    }

    @Bean(SCHEDULER)
    static TaskScheduler memberPurgeTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("member-purge-");
        return scheduler;
    }

    @Scheduled(cron = "0 30 19 * * *", zone = "UTC", scheduler = SCHEDULER)
    void purge() {
        try {
            log.atInfo()
                    .addKeyValue("event", "MEMBER_PURGE_DONE")
                    .addKeyValue("purged", memberPurgeService.run())
                    .log("회원 데이터 파기를 마쳤다");
        } catch (RuntimeException exception) {
            log.atError()
                    .addKeyValue("event", "MEMBER_PURGE_RUN_FAILED")
                    .setCause(exception)
                    .log("회원 데이터 파기를 끝내지 못했다. 다음 날 다시 처리한다");
        }
    }
}
