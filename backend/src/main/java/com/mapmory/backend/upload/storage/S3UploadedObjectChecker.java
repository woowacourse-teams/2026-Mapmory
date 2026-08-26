package com.mapmory.backend.upload.storage;

import com.mapmory.backend.common.exception.BusinessException;
import com.mapmory.backend.upload.UploadErrorCode;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;

/**
 * HeadObject로 객체 실존을 확인한다.
 *
 * 본문이 없는 메타데이터 호출이라 같은 리전 기준 수십 ms다. 앱이 PUT 200을 받은 뒤 보낸
 * 요청이라면 S3의 read-after-write 강한 일관성 덕분에 반드시 그 객체가 보인다. (ADR 0016)
 */
@Component
public class S3UploadedObjectChecker implements UploadedObjectChecker {

    private static final int NOT_FOUND = 404;
    private static final int FORBIDDEN = 403;

    private final S3Client s3Client;
    private final String bucket;

    public S3UploadedObjectChecker(
            S3Client s3Client,
            S3StorageProperties properties
    ) {
        this.s3Client = s3Client;
        this.bucket = properties.bucket();
    }

    @Override
    public boolean exists(String objectKey) {
        HeadObjectRequest request = HeadObjectRequest.builder()
                .bucket(bucket)
                .key(objectKey)
                .build();
        try {
            s3Client.headObject(request);
            return true;
        } catch (NoSuchKeyException exception) {
            return false;
        } catch (S3Exception exception) {
            // HeadObject는 응답 본문이 없어, SDK 버전에 따라 NoSuchKeyException 대신
            // 상태 코드만 담긴 S3Exception으로 오기도 한다.
            if (exception.statusCode() == NOT_FOUND) {
                return false;
            }
            // s3:ListBucket 권한이 없으면 S3는 "없는 객체"에도 404 대신 403을 준다.
            // 객체 존재 여부를 권한 없는 호출자에게 흘리지 않기 위한 동작이라, 403을 "없음"으로
            // 취급하면 멀쩡히 있는 사진을 거절하게 된다. 설정 문제로 따로 알린다.
            if (exception.statusCode() == FORBIDDEN) {
                throw failure(UploadErrorCode.STORAGE_ACCESS_DENIED, exception);
            }
            throw failure(UploadErrorCode.STORAGE_UNAVAILABLE, exception);
        } catch (SdkException exception) {
            throw failure(UploadErrorCode.STORAGE_UNAVAILABLE, exception);
        }
    }

    private BusinessException failure(UploadErrorCode errorCode, SdkException cause) {
        return new BusinessException(errorCode, errorCode.detail(), cause);
    }
}
