package com.mapmory.backend.sharevideo.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.mapmory.backend.upload.storage.S3StorageProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

class S3ShareVideoStorageTest {

    private final S3Client s3Client = mock(S3Client.class);

    @TempDir
    private Path tempDirectory;

    @Test
    void 설정한_버킷과_영상_Object_Key로_MP4를_업로드한다() throws IOException {
        S3ShareVideoStorage storage = storage("mapmory");
        Path videoPath = createVideo();

        String result = storage.upload(15L, videoPath);

        ArgumentCaptor<PutObjectRequest> request = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(request.capture(), any(software.amazon.awssdk.core.sync.RequestBody.class));
        assertThat(request.getValue().bucket()).isEqualTo("mapmory-test");
        assertThat(request.getValue().key()).isEqualTo("mapmory/share-videos/15/travel-timeline.mp4");
        assertThat(request.getValue().contentType()).isEqualTo("video/mp4");
        assertThat(result).isEqualTo("mapmory/share-videos/15/travel-timeline.mp4");
    }

    @Test
    void prefix의_앞뒤_슬래시는_떼고_붙인다() throws IOException {
        S3ShareVideoStorage storage = storage("/develop/");

        String result = storage.upload(15L, createVideo());

        assertThat(result).isEqualTo("develop/share-videos/15/travel-timeline.mp4");
    }

    private Path createVideo() throws IOException {
        Path videoPath = tempDirectory.resolve("result.mp4");
        Files.writeString(videoPath, "video");
        return videoPath;
    }

    private S3ShareVideoStorage storage(final String keyPrefix) {
        S3StorageProperties properties = new S3StorageProperties(
                "mapmory-test",
                "ap-northeast-2",
                keyPrefix,
                Duration.ofMinutes(5),
                new S3StorageProperties.Client(
                        Duration.ofSeconds(1),
                        Duration.ofSeconds(3),
                        3
                )
        );
        return new S3ShareVideoStorage(s3Client, properties);
    }
}
