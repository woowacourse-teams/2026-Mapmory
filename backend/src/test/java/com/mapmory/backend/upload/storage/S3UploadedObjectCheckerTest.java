package com.mapmory.backend.upload.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.mapmory.backend.common.exception.BusinessException;
import com.mapmory.backend.upload.UploadErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.http.SdkHttpResponse;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;

/**
 * 객체 없음과 저장소 확인 실패는 구분되어야 한다. (ADR 0016)
 *
 * 둘을 뭉뚱그리면 S3 장애 중에 앱이 "다시 업로드하라"고 안내하게 되는데,
 * 다시 올려도 같은 장애에 걸린다.
 */
class S3UploadedObjectCheckerTest {

    private final S3Client s3Client = mock(S3Client.class);
    private S3UploadedObjectChecker checker;

    @BeforeEach
    void setUp() {
        checker = new S3UploadedObjectChecker(
                s3Client,
                new S3StorageProperties("mapmory-test", "ap-northeast-2", "")
        );
    }

    @Test
    void 객체가_있으면_true를_반환한다() {
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenReturn(HeadObjectResponse.builder().build());

        assertThat(checker.exists("object-key")).isTrue();
    }

    @Test
    void 객체가_없으면_false를_반환한다() {
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenThrow(NoSuchKeyException.builder().message("not found").build());

        assertThat(checker.exists("object-key")).isFalse();
    }

    @Test
    void 상태_코드만_담긴_404도_없음으로_본다() {
        when(s3Client.headObject(any(HeadObjectRequest.class))).thenThrow(s3Exception(404));

        assertThat(checker.exists("object-key")).isFalse();
    }

    @Test
    void 저장소_확인이_실패하면_STORAGE_UNAVAILABLE로_알린다() {
        when(s3Client.headObject(any(HeadObjectRequest.class))).thenThrow(s3Exception(500));

        assertThatThrownBy(() -> checker.exists("object-key"))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(UploadErrorCode.STORAGE_UNAVAILABLE);
    }

    @Test
    void 연결_실패도_STORAGE_UNAVAILABLE로_알린다() {
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenThrow(SdkClientException.create("connection reset"));

        assertThatThrownBy(() -> checker.exists("object-key"))
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
