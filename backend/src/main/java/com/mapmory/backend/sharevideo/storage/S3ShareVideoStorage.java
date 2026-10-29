package com.mapmory.backend.sharevideo.storage;

import com.mapmory.backend.upload.storage.S3StorageProperties;
import java.nio.file.Path;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import java.util.UUID;

@Component
public class S3ShareVideoStorage implements ShareVideoStorage {

    private static final String CONTENT_TYPE = "video/mp4";

    private final S3Client s3Client;
    private final String bucket;
    private final String keyPrefix;

    public S3ShareVideoStorage(
            final S3Client s3Client,
            final S3StorageProperties properties
    ) {
        this.s3Client = s3Client;
        this.bucket = properties.bucket();
        this.keyPrefix = properties.keyPrefix();
    }

    @Override
    public String upload(final Long jobId, final Path videoPath) {
        String objectKey = createObjectKey(jobId);

        uploadAttempt(objectKey, videoPath);
        return objectKey;
    }

    @Override
    public String attemptObjectKey(final Long jobId, final String attemptId) {
        UUID uuid = UUID.fromString(attemptId);
        return keyPrefix + "/share-videos/%d/%s/travel-timeline.mp4".formatted(jobId, uuid);
    }

    @Override
    public void uploadAttempt(final String objectKey, final Path videoPath) {

        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucket)
                .key(objectKey)
                .contentType(CONTENT_TYPE)
                .build();

        s3Client.putObject(
                request,
                RequestBody.fromFile(videoPath)
        );

    }

    @Override
    public boolean exists(final String objectKey) {
        try {
            s3Client.headObject(HeadObjectRequest.builder().bucket(bucket).key(objectKey).build());
            return true;
        } catch (S3Exception exception) {
            if (exception.statusCode() == 404) {
                return false;
            }
            throw exception;
        }
    }

    private String createObjectKey(final Long jobId) {
        return keyPrefix + "/share-videos/%d/travel-timeline.mp4".formatted(jobId);
    }
}
