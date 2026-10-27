package com.mapmory.backend.upload.storage;

import com.mapmory.backend.common.exception.BusinessException;
import com.mapmory.backend.upload.UploadErrorCode;
import com.mapmory.backend.upload.service.port.UploadedObjectChecker;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;

@Component
public class S3UploadedObjectChecker implements UploadedObjectChecker {

    private static final int NOT_FOUND = 404;

    private final S3Client s3Client;
    private final String bucket;

    public S3UploadedObjectChecker(S3Client s3Client, S3StorageProperties properties) {
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
            if (exception.statusCode() == NOT_FOUND) {
                return false;
            }
            throw storageUnavailable(exception);
        } catch (SdkException exception) {
            throw storageUnavailable(exception);
        }
    }

    private BusinessException storageUnavailable(SdkException cause) {
        return new BusinessException(
                UploadErrorCode.STORAGE_UNAVAILABLE,
                UploadErrorCode.STORAGE_UNAVAILABLE.detail(),
                cause
        );
    }
}
