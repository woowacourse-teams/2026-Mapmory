package com.mapmory.backend.travelrecord.thumbnail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.mapmory.backend.sharevideo.worker.RenderActivity;
import com.mapmory.backend.travelrecord.domain.TravelRecordRepository;
import com.mapmory.backend.upload.service.port.ObjectDeleter;
import com.mapmory.backend.upload.storage.DerivedObjectKeys;
import com.mapmory.backend.upload.storage.S3StorageProperties;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.scheduling.annotation.Scheduled;
import software.amazon.awssdk.services.s3.S3Client;

class ThumbnailWorkerConfigTest {

    private static final S3StorageProperties S3 = new S3StorageProperties("bucket", "ap-northeast-2", "mapmory",
            Duration.ofMinutes(5), new S3StorageProperties.Client(Duration.ofSeconds(1), Duration.ofSeconds(3), 3,
            Duration.ofSeconds(10), Duration.ofSeconds(30), Duration.ofSeconds(10), Duration.ofSeconds(30)));

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(ThumbnailWorkerConfig.class)
            .withBean(S3Client.class, () -> mock(S3Client.class))
            .withBean(S3StorageProperties.class, () -> S3)
            .withBean(DerivedObjectKeys.class, () -> new DerivedObjectKeys(S3))
            .withBean(ObjectDeleter.class, () -> mock(ObjectDeleter.class))
            .withBean(TravelRecordRepository.class, () -> mock(TravelRecordRepository.class))
            .withBean(MeterRegistry.class, SimpleMeterRegistry::new)
            .withBean(Clock.class, Clock::systemUTC)
            .withPropertyValues(
                    "media.thumbnail.worker.poll-delay=10s",
                    "media.thumbnail.worker.batch-size=20",
                    "media.thumbnail.worker.max-edge=1080",
                    "media.thumbnail.worker.process-timeout=30s",
                    "media.thumbnail.worker.max-failures=3",
                    "media.thumbnail.worker.script-directory=../thumbnail",
                    "media.thumbnail.worker.python-command=python3",
                    "media.thumbnail.worker.work-directory=/tmp/mapmory/thumbnails",
                    "media.thumbnail.worker.transfer-timeout=30s");

    @Test
    void 기본값이면_워커와_전용_스케줄러가_뜨지_않는다() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(ThumbnailWorker.class);
            assertThat(context).doesNotHaveBean("thumbnailTaskScheduler");
        });
    }

    @Test
    void 켜면_워커와_전용_스케줄러가_뜬다() {
        contextRunner.withPropertyValues("media.thumbnail.worker.enabled=true").run(context -> {
            assertThat(context).hasSingleBean(ThumbnailWorker.class);
            assertThat(context).hasBean("thumbnailTaskScheduler");
        });
    }

    // 공유 영상 워커를 끈 서버에서도 썸네일 워커만 켤 수 있다. 그때는 영상 작업이 없다고 본다.
    @Test
    void 공유_영상_워커가_없어도_뜬다() {
        contextRunner.withPropertyValues("media.thumbnail.worker.enabled=true")
                .run(context -> assertThat(context).doesNotHaveBean(RenderActivity.class).hasSingleBean(ThumbnailWorker.class));
    }

    @Test
    void 워커는_전용_스케줄러에서_돈다() throws NoSuchMethodException {
        Scheduled scheduled = ThumbnailWorker.class.getMethod("processNext").getAnnotation(Scheduled.class);

        assertThat(scheduled.scheduler()).isEqualTo("thumbnailTaskScheduler");
    }
}
