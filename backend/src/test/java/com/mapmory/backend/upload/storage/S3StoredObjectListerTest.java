package com.mapmory.backend.upload.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mapmory.backend.upload.service.port.StoredObject;
import com.mapmory.backend.upload.service.port.StoredObjectPage;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.S3Object;

class S3StoredObjectListerTest {

    private static final String BUCKET = "techcourse-project-2026";
    private static final Duration DELETE_ATTEMPT = Duration.ofSeconds(10);
    private static final Duration DELETE_TOTAL = Duration.ofSeconds(30);
    private static final Instant UPLOADED_AT = Instant.parse("2026-11-01T00:00:00Z");

    private final S3Client s3Client = mock(S3Client.class);
    private final S3StoredObjectLister lister = new S3StoredObjectLister(s3Client, new S3StorageProperties(
            BUCKET, "ap-northeast-2", "mapmory", Duration.ofMinutes(5), new S3StorageProperties.Client(
            Duration.ofSeconds(1), Duration.ofSeconds(3), 3,
            Duration.ofSeconds(10), Duration.ofSeconds(30), DELETE_ATTEMPT, DELETE_TOTAL)));

    /**
     * 공용 버킷이라 다른 팀 경로나 공유 영상 경로를 읽지 않는다.
     */
    @Test
    void 우리_여행_기록_경로만_읽는다() {
        when(s3Client.listObjectsV2(any(ListObjectsV2Request.class)))
                .thenReturn(ListObjectsV2Response.builder().isTruncated(false).build());

        lister.listTravelRecordObjects(null);

        ArgumentCaptor<ListObjectsV2Request> request = ArgumentCaptor.forClass(ListObjectsV2Request.class);
        verify(s3Client).listObjectsV2(request.capture());
        assertThat(request.getValue().bucket()).isEqualTo(BUCKET);
        assertThat(request.getValue().prefix()).isEqualTo("mapmory/travel-records/");
        assertThat(request.getValue().maxKeys()).isEqualTo(1000);
        assertThat(request.getValue().overrideConfiguration().orElseThrow().apiCallAttemptTimeout())
                .contains(DELETE_ATTEMPT);
        assertThat(request.getValue().overrideConfiguration().orElseThrow().apiCallTimeout())
                .contains(DELETE_TOTAL);
    }

    @Test
    void 키와_올라간_시각을_돌려준다() {
        when(s3Client.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(ListObjectsV2Response.builder()
                .contents(S3Object.builder().key("mapmory/travel-records/1/a.jpg").lastModified(UPLOADED_AT).build())
                .isTruncated(false)
                .build());

        StoredObjectPage page = lister.listTravelRecordObjects(null);

        assertThat(page.objects()).containsExactly(new StoredObject("mapmory/travel-records/1/a.jpg", UPLOADED_AT));
        assertThat(page.hasNext()).isFalse();
    }

    @Test
    void 다음_쪽이_있으면_토큰을_돌려주고_그_토큰으로_이어_읽는다() {
        when(s3Client.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(ListObjectsV2Response.builder()
                .isTruncated(true)
                .nextContinuationToken("next")
                .build());

        StoredObjectPage page = lister.listTravelRecordObjects("current");

        assertThat(page.nextToken()).isEqualTo("next");
        ArgumentCaptor<ListObjectsV2Request> request = ArgumentCaptor.forClass(ListObjectsV2Request.class);
        verify(s3Client).listObjectsV2(request.capture());
        assertThat(request.getValue().continuationToken()).isEqualTo("current");
    }
}
