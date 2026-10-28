package com.mapmory.backend.sharevideo.worker;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
@EnableScheduling
@EnableConfigurationProperties(ShareVideoWorkerProperties.class)
public class ShareVideoWorkerConfig {

    @Bean(name = "shareVideoTaskScheduler")
    @ConditionalOnProperty(
            prefix = "share-video.worker",
            name = "enabled",
            havingValue = "true"
    )
    public TaskScheduler shareVideoTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();

        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("share-video-worker-");

        return scheduler;
    }
}
