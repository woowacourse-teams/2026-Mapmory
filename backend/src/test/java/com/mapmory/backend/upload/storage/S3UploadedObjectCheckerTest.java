package com.mapmory.backend.upload.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mapmory.backend.common.exception.BusinessException;
import com.mapmory.backend.upload.UploadErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.http.SdkHttpResponse;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;

class S3UploadedObjectCheckerTest {

    private final S3Client s3Client = mock(S3Client.class);
    private S3UploadedObjectChecker checker;

    @BeforeEach
    void setUp() {
        checker = new S3UploadedObjectChecker(
                s3Client,
                new S3StorageProperties("techcourse-project-2026", "ap-northeast-2", "mapmory", null)
        );
    }

    @Test
    void 설정된_버킷과_요청받은_키로_객체를_확인한다() {
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenReturn(HeadObjectResponse.builder().build());

        assertThat(checker.exists("mapmory/travel-records/1/image.jpg")).isTrue();

        ArgumentCaptor<HeadObjectRequest> request = ArgumentCaptor.forClass(HeadObjectRequest.class);
        verify(s3Client).headObject(request.capture());
        assertThat(request.getValue().bucket()).isEqualTo("techcourse-project-2026");
        assertThat(request.getValue().key()).isEqualTo("mapmory/travel-records/1/image.jpg");
    }

    @Test
    void 객체가_없으면_false를_반환한다() {
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenThrow(NoSuchKeyException.builder().message("not found").build());

        assertThat(checker.exists("missing-key")).isFalse();
    }

    @Test
    void 상태_코드만_담긴_404도_객체_없음으로_판단한다() {
        when(s3Client.headObject(any(HeadObjectRequest.class))).thenThrow(s3Exception(404));

        assertThat(checker.exists("missing-key")).isFalse();
    }

    @Test
    void 권한이나_S3_장애는_객체_없음과_구분한다() {
        when(s3Client.headObject(any(HeadObjectRequest.class))).thenThrow(s3Exception(403));

        assertStorageUnavailable(() -> checker.exists("object-key"));
    }

    @Test
    void 네트워크_오류는_저장소_장애로_알린다() {
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenThrow(SdkClientException.create("connection reset"));

        assertStorageUnavailable(() -> checker.exists("object-key"));
    }

    private static void assertStorageUnavailable(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(UploadErrorCode.STORAGE_UNAVAILABLE);
    }

    private static S3Exception s3Exception(int statusCode) {
        return (S3Exception) S3Exception.builder()
                .message("s3 failure")
                .statusCode(statusCode)
                .awsErrorDetails(AwsErrorDetails.builder()
                        .sdkHttpResponse(SdkHttpResponse.builder().statusCode(statusCode).build())
                        .build())
                .build();
    }
}
