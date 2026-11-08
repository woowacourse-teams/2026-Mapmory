package com.mapmory.backend.travelrecord.thumbnail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.mapmory.backend.upload.storage.DerivedObjectKeys;
import com.mapmory.backend.upload.storage.S3StorageProperties;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

class S3ThumbnailStorageTest {

    private static final String ORIGINAL = "mapmory/travel-records/7/abc.heic";
    private static final String THUMBNAIL = "mapmory/derived/thumb/7/abc.webp";
    private static final Duration TRANSFER = Duration.ofSeconds(30);

    private final S3Client s3Client = mock(S3Client.class);
    private final S3StorageProperties s3Properties = new S3StorageProperties("bucket", "ap-northeast-2", "mapmory",
            Duration.ofMinutes(5), new S3StorageProperties.Client(Duration.ofSeconds(1), Duration.ofSeconds(3), 3,
            Duration.ofSeconds(10), Duration.ofSeconds(30), Duration.ofSeconds(10), Duration.ofSeconds(30)));
    private final S3ThumbnailStorage storage = new S3ThumbnailStorage(s3Client, s3Properties,
            new DerivedObjectKeys(s3Properties),
            ThumbnailWorkerPropertiesFixture.with(Path.of("."), "python3", Duration.ofSeconds(30)));

    @TempDir
    Path directory;

    @Test
    void 썸네일을_WebP와_오래_캐시해도_되는_헤더로_올린다() throws Exception {
        Path file = Files.writeString(directory.resolve("t.webp"), "webp");

        storage.upload(file, THUMBNAIL);

        ArgumentCaptor<PutObjectRequest> request = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(request.capture(), any(RequestBody.class));
        assertThat(request.getValue().bucket()).isEqualTo("bucket");
        assertThat(request.getValue().key()).isEqualTo(THUMBNAIL);
        assertThat(request.getValue().contentType()).isEqualTo("image/webp");
        assertThat(request.getValue().cacheControl()).isEqualTo("max-age=31536000, immutable");
        assertThat(request.getValue().overrideConfiguration().orElseThrow().apiCallAttemptTimeout()).contains(TRANSFER);
    }

    // 워커가 원본을 덮어쓰거나 다른 팀 경로에 쓰지 못하게 파생 경로 밖에는 쓰지도 지우지도 않는다.
    @Test
    void 파생_경로_밖에는_올리지_않는다() {
        Path file = directory.resolve("t.webp");

        assertThatThrownBy(() -> storage.upload(file, ORIGINAL)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> storage.exists("other-team/derived/thumb/7/abc.webp"))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(s3Client);
    }

    @Test
    void 원본은_여행_기록_경로에서만_내려받는다() {
        Path file = directory.resolve("o.heic");

        storage.download(ORIGINAL, file);

        ArgumentCaptor<GetObjectRequest> request = ArgumentCaptor.forClass(GetObjectRequest.class);
        verify(s3Client).getObject(request.capture(), any(ResponseTransformer.class));
        assertThat(request.getValue().key()).isEqualTo(ORIGINAL);
        assertThat(request.getValue().overrideConfiguration().orElseThrow().apiCallAttemptTimeout()).contains(TRANSFER);
        assertThatThrownBy(() -> storage.download(THUMBNAIL, file)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 없는_썸네일은_없다고_답하고_그_밖의_오류는_던진다() {
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenThrow(S3Exception.builder().statusCode(404).build())
                .thenReturn(HeadObjectResponse.builder().build())
                .thenThrow(S3Exception.builder().statusCode(403).build());

        assertThat(storage.exists(THUMBNAIL)).isFalse();
        assertThat(storage.exists(THUMBNAIL)).isTrue();
        assertThatThrownBy(() -> storage.exists(THUMBNAIL)).isInstanceOf(S3Exception.class);
    }
}
