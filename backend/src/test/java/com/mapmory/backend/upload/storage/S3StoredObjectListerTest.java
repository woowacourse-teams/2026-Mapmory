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
            BUCKET, "ap-northeast-2", "mapmory/dev", Duration.ofMinutes(5), new S3StorageProperties.Client(
            Duration.ofSeconds(1), Duration.ofSeconds(3), 3,
            Duration.ofSeconds(10), Duration.ofSeconds(30), DELETE_ATTEMPT, DELETE_TOTAL)));

    @Test
    void 공용_버킷에서_우리_여행_기록_경로만_읽는다() {
        respondWith(ListObjectsV2Response.builder().isTruncated(false).build());

        lister.listTravelRecordObjects(null);

        ListObjectsV2Request request = sentRequest();
        assertThat(request.bucket()).isEqualTo(BUCKET);
        assertThat(request.prefix()).isEqualTo("mapmory/dev/travel-records/");
        assertThat(request.maxKeys()).isEqualTo(1000);
        assertThat(request.continuationToken()).isNull();
    }

    @Test
    void 키와_올라간_시각을_옮긴다() {
        respondWith(ListObjectsV2Response.builder()
                .isTruncated(false)
                .contents(S3Object.builder().key("mapmory/dev/travel-records/1/a.jpg").lastModified(UPLOADED_AT).build())
                .build());

        StoredObjectPage page = lister.listTravelRecordObjects(null);

        assertThat(page.objects()).containsExactly(new StoredObject("mapmory/dev/travel-records/1/a.jpg", UPLOADED_AT));
    }

    @Test
    void 잘린_응답이면_다음_쪽_토큰을_돌려주고_그_토큰으로_이어_읽는다() {
        respondWith(ListObjectsV2Response.builder().isTruncated(true).nextContinuationToken("next").build());

        StoredObjectPage page = lister.listTravelRecordObjects("current");

        assertThat(page.nextToken()).isEqualTo("next");
        assertThat(sentRequest().continuationToken()).isEqualTo("current");
    }

    @Test
    void 마지막_쪽이면_토큰이_없다() {
        respondWith(ListObjectsV2Response.builder().isTruncated(false).nextContinuationToken("ignored").build());

        assertThat(lister.listTravelRecordObjects(null).nextToken()).isNull();
    }

    @Test
    void 한_쪽을_삭제와_같은_시간_한도로_기다린다() {
        respondWith(ListObjectsV2Response.builder().isTruncated(false).build());

        lister.listTravelRecordObjects(null);

        ListObjectsV2Request request = sentRequest();
        assertThat(request.overrideConfiguration().orElseThrow().apiCallAttemptTimeout()).contains(DELETE_ATTEMPT);
        assertThat(request.overrideConfiguration().orElseThrow().apiCallTimeout()).contains(DELETE_TOTAL);
    }

    private void respondWith(ListObjectsV2Response response) {
        when(s3Client.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(response);
    }

    private ListObjectsV2Request sentRequest() {
        ArgumentCaptor<ListObjectsV2Request> captor = ArgumentCaptor.forClass(ListObjectsV2Request.class);
        verify(s3Client).listObjectsV2(captor.capture());
        return captor.getValue();
    }
}
