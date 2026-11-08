package com.mapmory.backend.travelrecord.thumbnail;

import com.mapmory.backend.sharevideo.worker.RenderActivity;
import com.mapmory.backend.travelrecord.domain.TravelRecordRepository;
import com.mapmory.backend.upload.service.port.ObjectDeleter;
import com.mapmory.backend.upload.storage.DerivedObjectKeys;
import com.mapmory.backend.upload.storage.S3StorageProperties;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import software.amazon.awssdk.services.s3.S3Client;

/**
 * 썸네일 워커는 렌더러 서버에서만 켠다(media.thumbnail.worker.enabled). 설정 값은 어디서나 검증해, 켜는 서버에서만 기동이
 * 실패하는 일을 막는다.
 */
@Configuration
@EnableScheduling
@EnableConfigurationProperties(ThumbnailWorkerProperties.class)
public class ThumbnailWorkerConfig {

    @Bean(name = "thumbnailTaskScheduler")
    @ConditionalOnProperty(prefix = "media.thumbnail.worker", name = "enabled", havingValue = "true")
    public TaskScheduler thumbnailTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("thumbnail-worker-");
        return scheduler;
    }

    @Bean
    @ConditionalOnProperty(prefix = "media.thumbnail.worker", name = "enabled", havingValue = "true")
    public ThumbnailWorker thumbnailWorker(
            TravelRecordRepository travelRecordRepository,
            DerivedObjectKeys derivedObjectKeys,
            S3Client s3Client,
            S3StorageProperties s3Properties,
            ObjectDeleter objectDeleter,
            ObjectProvider<RenderActivity> renderActivity,
            ThumbnailWorkerProperties properties,
            MeterRegistry meterRegistry,
            Clock clock
    ) {
        return new ThumbnailWorker(
                travelRecordRepository,
                derivedObjectKeys,
                new S3ThumbnailStorage(s3Client, s3Properties, derivedObjectKeys, objectDeleter, properties),
                new ProcessThumbnailMaker(properties),
                // 공유 영상 워커를 끈 서버에서는 영상 작업이 없다.
                renderActivity.getIfAvailable(() -> () -> false),
                properties,
                meterRegistry,
                clock
        );
    }
}
