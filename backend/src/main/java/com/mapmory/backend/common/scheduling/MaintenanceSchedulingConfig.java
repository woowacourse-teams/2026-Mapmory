package com.mapmory.backend.common.scheduling;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * 하루 한 번 도는 정리 배치(기기 토큰, 그룹 멤버, 장소 한도, refresh 토큰, 떨어져 나간 S3 객체, 기록에 붙지 않은 S3 객체 대조) 전용 스케줄러. 알림 발송과 스레드를 나눠 서로 밀리지 않게 한다.
 *
 * <p>스케줄러를 지정하지 않으면 어느 스레드에서 도는지가 떠 있는 TaskScheduler 빈에 따라 달라지므로, 정리 배치는 이 이름을 명시한다.
 * 정리끼리는 차례로 돌아도 되므로 스레드는 하나다. 멱등이라 배포로 끊겨도 다음 차례에 이어서 지운다.
 *
 * <p>API를 받는 서버에서만 켠다. 이 빈이 없는 서버에서 이름을 명시한 @Scheduled가 뜨면 기동이 실패하므로,
 * 이 스케줄러를 쓰는 클래스에는 같은 조건을 건다.
 */
@Configuration
@EnableScheduling
public class MaintenanceSchedulingConfig {

    public static final String SCHEDULER = "maintenanceTaskScheduler";

    @Bean(SCHEDULER)
    @ConditionalOnProperty(prefix = "notification.outbox", name = "enabled", havingValue = "true", matchIfMissing = true)
    public TaskScheduler maintenanceTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("maintenance-");
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(10);
        return scheduler;
    }
}
