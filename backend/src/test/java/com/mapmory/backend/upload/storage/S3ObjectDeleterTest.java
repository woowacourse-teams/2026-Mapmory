package com.mapmory.backend.upload.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectsResponse;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import software.amazon.awssdk.services.s3.model.S3Error;

class S3ObjectDeleterTest {

    private static final String BUCKET = "techcourse-project-2026";
    private static final Duration DELETE_ATTEMPT = Duration.ofSeconds(10);
    private static final Duration DELETE_TOTAL = Duration.ofSeconds(30);

    private final S3Client s3Client = mock(S3Client.class);
    private final S3ObjectDeleter deleter = new S3ObjectDeleter(s3Client, new S3StorageProperties(
            BUCKET, "ap-northeast-2", "mapmory", Duration.ofMinutes(5), new S3StorageProperties.Client(
            Duration.ofSeconds(1), Duration.ofSeconds(3), 3,
            Duration.ofSeconds(10), Duration.ofSeconds(30), DELETE_ATTEMPT, DELETE_TOTAL)));

    @Test
    void 설정된_버킷에서_요청받은_키를_지운다() {
        when(s3Client.deleteObjects(any(DeleteObjectsRequest.class))).thenReturn(success());

        List<String> failed = deleter.delete(List.of(key(1), key(2)));

        assertThat(failed).isEmpty();
        ArgumentCaptor<DeleteObjectsRequest> request = ArgumentCaptor.forClass(DeleteObjectsRequest.class);
        verify(s3Client).deleteObjects(request.capture());
        assertThat(request.getValue().bucket()).isEqualTo(BUCKET);
        assertThat(keysOf(request.getValue())).containsExactly(key(1), key(2));
        assertThat(request.getValue().overrideConfiguration().orElseThrow().apiCallAttemptTimeout())
                .contains(DELETE_ATTEMPT);
        assertThat(request.getValue().overrideConfiguration().orElseThrow().apiCallTimeout())
                .contains(DELETE_TOTAL);
    }

    @Test
    void 키가_없으면_S3를_부르지_않는다() {
        assertThat(deleter.delete(List.of())).isEmpty();

        verifyNoInteractions(s3Client);
    }

    @Test
    void 천_개가_넘으면_천_개씩_나눠_지운다() {
        when(s3Client.deleteObjects(any(DeleteObjectsRequest.class))).thenReturn(success());

        deleter.delete(IntStream.rangeClosed(1, 1001).mapToObj(this::key).toList());

        ArgumentCaptor<DeleteObjectsRequest> request = ArgumentCaptor.forClass(DeleteObjectsRequest.class);
        verify(s3Client, times(2)).deleteObjects(request.capture());
        assertThat(request.getAllValues()).extracting(this::keyCount).containsExactly(1000, 1);
    }

    @Test
    void 같은_키는_한_번만_지운다() {
        when(s3Client.deleteObjects(any(DeleteObjectsRequest.class))).thenReturn(success());

        deleter.delete(List.of(key(1), key(1)));

        ArgumentCaptor<DeleteObjectsRequest> request = ArgumentCaptor.forClass(DeleteObjectsRequest.class);
        verify(s3Client).deleteObjects(request.capture());
        assertThat(keysOf(request.getValue())).containsExactly(key(1));
    }

    @Test
    void 일부_키가_실패하면_실패한_키를_돌려준다() {
        when(s3Client.deleteObjects(any(DeleteObjectsRequest.class))).thenReturn(DeleteObjectsResponse.builder()
                .errors(S3Error.builder().key(key(2)).code("AccessDenied").message("denied").build())
                .build());

        assertThat(deleter.delete(List.of(key(1), key(2)))).containsExactly(key(2));
    }

    @Test
    void 호출이_실패한_묶음의_키를_돌려주고_다음_묶음은_지운다() {
        when(s3Client.deleteObjects(any(DeleteObjectsRequest.class)))
                .thenThrow(SdkClientException.create("timeout"))
                .thenReturn(success());

        List<String> failed = deleter.delete(IntStream.rangeClosed(1, 1001).mapToObj(this::key).toList());

        assertThat(failed).hasSize(1000).contains(key(1), key(1000)).doesNotContain(key(1001));
        verify(s3Client, times(2)).deleteObjects(any(DeleteObjectsRequest.class));
    }

    /**
     * 공용 버킷이라 다른 팀 객체를 지우면 되돌릴 수 없다. prefix가 같은 글자로 시작하는 다른 경로도 막는다.
     */
    @Test
    void 우리_prefix_밖의_키가_하나라도_있으면_아무것도_지우지_않는다() {
        for (String foreign : List.of("other-team/a.jpg", "mapmory-other/a.jpg", "a.jpg")) {
            assertThatThrownBy(() -> deleter.delete(List.of(key(1), foreign)))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        verifyNoInteractions(s3Client);
    }

    private String key(int number) {
        return "mapmory/travel-records/1/" + number + ".jpg";
    }

    private DeleteObjectsResponse success() {
        return DeleteObjectsResponse.builder().build();
    }

    private List<String> keysOf(DeleteObjectsRequest request) {
        return request.delete().objects().stream().map(ObjectIdentifier::key).toList();
    }

    private int keyCount(DeleteObjectsRequest request) {
        return request.delete().objects().size();
    }
}
