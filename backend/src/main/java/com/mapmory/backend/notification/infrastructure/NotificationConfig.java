package com.mapmory.backend.notification.infrastructure;

import com.mapmory.backend.notification.application.port.PushSender;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * 알림은 앨범 쓰기와 같은 트랜잭션에 아웃박스로 적고, 이 서버의 스케줄러가 꺼내 보낸다(ADR 0022 2026-10-29 보완).
 * 발송이 느려도 올리기 응답을 잡지 않고, 배포로 서버가 내려가도 알림을 잃지 않는다.
 *
 * <p>스케줄러는 공유 영상 작업과 스레드를 나눠 쓰지 않게 따로 둔다. API 서버에서는 이것이 유일한 스케줄러라
 * 스케줄러를 지정하지 않은 장소 한도 정리(하루 한 번)도 이 스레드에서 돈다. 정리는 금방 끝나 알림을 오래 막지 않는다.
 * 렌더러 서버처럼 API를 받지 않는 곳에서는 {@code NOTIFICATION_OUTBOX_ENABLED=false}로 끈다.
 */
@Configuration
@EnableScheduling
public class NotificationConfig {

    public static final String SCHEDULER = "notificationTaskScheduler";

    @Bean(SCHEDULER)
    @ConditionalOnProperty(prefix = "notification.outbox", name = "enabled", havingValue = "true", matchIfMissing = true)
    public TaskScheduler notificationTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("notification-");
        // 배포로 내릴 때 보내던 차례가 끝나기를 기다린다. 끊으면 잠금(5분)이 지나 같은 알림이 다시 간다.
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(30);
        return scheduler;
    }

    // 일반 설정 클래스의 @ConditionalOnMissingBean은 먼저 등록된 빈만 본다. FCM 어댑터는 이 클래스에서 이 메서드보다 먼저 선언한다.
    @Bean
    @ConditionalOnMissingBean(PushSender.class)
    public PushSender loggingPushSender() {
        return new LoggingPushSender();
    }
}
