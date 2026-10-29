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
 * <p>스케줄러는 공유 영상 작업·장소 한도 정리와 스레드를 나눠 쓰지 않게 따로 둔다.
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
        return scheduler;
    }

    // 일반 설정 클래스의 @ConditionalOnMissingBean은 먼저 등록된 빈만 본다. FCM 어댑터는 이 클래스에서 이 메서드보다 먼저 선언한다.
    @Bean
    @ConditionalOnMissingBean(PushSender.class)
    public PushSender loggingPushSender() {
        return new LoggingPushSender();
    }
}
