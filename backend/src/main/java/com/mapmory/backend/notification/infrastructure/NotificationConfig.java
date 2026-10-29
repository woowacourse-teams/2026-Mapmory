package com.mapmory.backend.notification.infrastructure;

import com.mapmory.backend.notification.application.port.PushSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 알림은 앨범 쓰기가 커밋된 뒤 요청 스레드 밖에서 보낸다(ADR 0022 결정 7). 발송이 느려도 올리기 응답을 잡지 않는다.
 */
@Configuration
@EnableAsync
public class NotificationConfig {

    public static final String EXECUTOR = "notificationExecutor";

    private static final Logger log = LoggerFactory.getLogger(NotificationConfig.class);

    // 대기열이 차면 그 알림은 버린다. 알림 하나를 잃는 쪽이 요청 스레드를 붙잡는 쪽보다 낫다.
    @Bean(EXECUTOR)
    public TaskExecutor notificationExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(1_000);
        executor.setThreadNamePrefix("notification-");
        executor.setRejectedExecutionHandler((task, pool) -> log.atWarn()
                .addKeyValue("event", "PUSH_DROPPED")
                .log("알림 대기열이 가득 차 버렸다"));
        executor.initialize();
        return executor;
    }

    // 일반 설정 클래스의 @ConditionalOnMissingBean은 먼저 등록된 빈만 본다. FCM 어댑터는 이 클래스에서 이 메서드보다 먼저 선언한다.
    @Bean
    @ConditionalOnMissingBean(PushSender.class)
    public PushSender loggingPushSender() {
        return new LoggingPushSender();
    }
}
