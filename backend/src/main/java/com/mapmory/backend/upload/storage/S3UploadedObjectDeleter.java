package com.mapmory.backend.upload.storage;

import java.util.List;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.Delete;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectsResponse;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import software.amazon.awssdk.services.s3.model.S3Error;

@Component
public class S3UploadedObjectDeleter implements UploadedObjectDeleter {

    private static final String TRAVEL_RECORDS_PREFIX = "travel-records/";

    private final S3Client s3Client;
    private final String bucket;
    private final String managedPrefix;

    public S3UploadedObjectDeleter(S3Client s3Client, S3StorageProperties properties) {
        this.s3Client = s3Client;
        this.bucket = properties.bucket();
        String keyPrefix = properties.normalizedKeyPrefix();
        this.managedPrefix = keyPrefix.isEmpty()
                ? TRAVEL_RECORDS_PREFIX
                : keyPrefix + "/" + TRAVEL_RECORDS_PREFIX;
    }

    @Override
    public void deleteAll(List<String> objectKeys) {
        if (objectKeys.isEmpty()) {
            return;
        }
        validateManagedKeys(objectKeys);

        List<ObjectIdentifier> objects = objectKeys.stream()
                .map(objectKey -> ObjectIdentifier.builder().key(objectKey).build())
                .toList();
        DeleteObjectsRequest request = DeleteObjectsRequest.builder()
                .bucket(bucket)
                .delete(Delete.builder().objects(objects).quiet(true).build())
                .build();

        DeleteObjectsResponse response = s3Client.deleteObjects(request);
        if (response.hasErrors()) {
            String failedKeys = response.errors().stream()
                    .map(S3Error::key)
                    .toList()
                    .toString();
            throw new IllegalStateException("S3 객체 일부를 삭제하지 못했습니다: " + failedKeys);
        }
    }

    private void validateManagedKeys(List<String> objectKeys) {
        List<String> unmanagedKeys = objectKeys.stream()
                .filter(objectKey -> objectKey == null || !objectKey.startsWith(managedPrefix))
                .toList();
        if (!unmanagedKeys.isEmpty()) {
            throw new IllegalArgumentException("관리 범위 밖의 S3 객체는 삭제할 수 없습니다.");
        }
    }
}
