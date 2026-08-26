package com.mapmory.backend.upload.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.http.SdkHttpResponse;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.S3Object;

/**
 * 권한 누락은 로컬·CI에서 드러나지 않으므로 기동 시점에 확인한다. (ADR 0016)
 *
 * 없는 키만 조회해서는 s3:GetObject 를 검증할 수 없다. s3:ListBucket 만 있어도 404가 오기
 * 때문이다. 그래서 실제로 있는 객체를 찾아 조회하는지가 이 진단의 핵심이다.
 *
 * 무엇을 로그로 남기는지는 검증하지 않는다. 어떤 경우에도 기동을 막지 않는 것과,
 * 올바른 대상을 조회하는 것만 고정한다.
 */
class S3PermissionSelfCheckTest {

    private final S3Client s3Client = mock(S3Client.class);

    @Test
    void 실제로_있는_객체를_조회해_GetObject까지_확인한다() {
        S3PermissionSelfCheck selfCheck = selfCheck("mapmory");
        givenObjectExists("mapmory/travel-records/10/photo.jpg");
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenReturn(HeadObjectResponse.builder().build());

        selfCheck.verifyLookupPermissions();

        ArgumentCaptor<HeadObjectRequest> request = ArgumentCaptor.forClass(HeadObjectRequest.class);
        verify(s3Client).headObject(request.capture());
        assertThat(request.getValue().key()).isEqualTo("mapmory/travel-records/10/photo.jpg");
    }

    @Test
    void 우리_prefix_아래에서만_객체를_찾는다() {
        S3PermissionSelfCheck selfCheck = selfCheck("mapmory");
        givenNoObjects();

        selfCheck.verifyLookupPermissions();

        ArgumentCaptor<ListObjectsV2Request> request = ArgumentCaptor.forClass(ListObjectsV2Request.class);
        verify(s3Client).listObjectsV2(request.capture());
        assertThat(request.getValue().prefix()).isEqualTo("mapmory/travel-records/");
    }

    @Test
    void prefix가_없으면_붙이지_않는다() {
        S3PermissionSelfCheck selfCheck = selfCheck("");
        givenNoObjects();

        selfCheck.verifyLookupPermissions();

        ArgumentCaptor<ListObjectsV2Request> request = ArgumentCaptor.forClass(ListObjectsV2Request.class);
        verify(s3Client).listObjectsV2(request.capture());
        assertThat(request.getValue().prefix()).isEqualTo("travel-records/");
    }

    @Test
    void 객체가_하나도_없으면_조회를_시도하지_않는다() {
        S3PermissionSelfCheck selfCheck = selfCheck("mapmory");
        givenNoObjects();

        selfCheck.verifyLookupPermissions();

        verify(s3Client, never()).headObject(any(HeadObjectRequest.class));
    }

    @Test
    void 목록_조회가_거부되어도_기동을_막지_않는다() {
        S3PermissionSelfCheck selfCheck = selfCheck("mapmory");
        when(s3Client.listObjectsV2(any(ListObjectsV2Request.class))).thenThrow(s3Exception(403));

        assertThatCode(selfCheck::verifyLookupPermissions).doesNotThrowAnyException();
    }

    @Test
    void 객체_조회가_거부되어도_기동을_막지_않는다() {
        S3PermissionSelfCheck selfCheck = selfCheck("mapmory");
        givenObjectExists("mapmory/travel-records/10/photo.jpg");
        when(s3Client.headObject(any(HeadObjectRequest.class))).thenThrow(s3Exception(403));

        assertThatCode(selfCheck::verifyLookupPermissions).doesNotThrowAnyException();
    }

    @Test
    void 저장소가_응답하지_않아도_기동을_막지_않는다() {
        S3PermissionSelfCheck selfCheck = selfCheck("mapmory");
        when(s3Client.listObjectsV2(any(ListObjectsV2Request.class)))
                .thenThrow(SdkClientException.create("connection reset"));

        assertThatCode(selfCheck::verifyLookupPermissions).doesNotThrowAnyException();
    }

    private void givenObjectExists(String objectKey) {
        when(s3Client.listObjectsV2(any(ListObjectsV2Request.class)))
                .thenReturn(ListObjectsV2Response.builder()
                        .contents(S3Object.builder().key(objectKey).build())
                        .build());
    }

    private void givenNoObjects() {
        when(s3Client.listObjectsV2(any(ListObjectsV2Request.class)))
                .thenReturn(ListObjectsV2Response.builder().build());
    }

    private S3PermissionSelfCheck selfCheck(String keyPrefix) {
        return new S3PermissionSelfCheck(
                s3Client,
                new S3StorageProperties("mapmory-test", "ap-northeast-2", keyPrefix)
        );
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
