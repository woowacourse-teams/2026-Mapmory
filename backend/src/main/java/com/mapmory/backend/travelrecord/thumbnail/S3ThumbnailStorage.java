package com.mapmory.backend.travelrecord.thumbnail;

import com.mapmory.backend.upload.service.port.ObjectDeleter;
import com.mapmory.backend.upload.storage.DerivedObjectKeys;
import com.mapmory.backend.upload.storage.S3StorageProperties;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

/**
 * 키에 UUID가 들어 있어 같은 키의 내용은 바뀌지 않는다. 그래서 썸네일은 CloudFront와 앱이 오래 캐시해도 된다.
 */
public class S3ThumbnailStorage implements ThumbnailStorage {

    private static final String CONTENT_TYPE = "image/webp";
    private static final String CACHE_CONTROL = "private, max-age=31536000, immutable";

    private final S3Client s3Client;
    private final String bucket;
    private final String travelRecordsDirectory;
    private final DerivedObjectKeys derivedObjectKeys;
    private final ObjectDeleter objectDeleter;
    private final Duration transferTimeout;

    public S3ThumbnailStorage(
            S3Client s3Client,
            S3StorageProperties s3Properties,
            DerivedObjectKeys derivedObjectKeys,
            ObjectDeleter objectDeleter,
            ThumbnailWorkerProperties workerProperties
    ) {
        this.s3Client = s3Client;
        this.bucket = s3Properties.bucket();
        this.travelRecordsDirectory = s3Properties.travelRecordsDirectory();
        this.derivedObjectKeys = derivedObjectKeys;
        this.objectDeleter = objectDeleter;
        this.transferTimeout = workerProperties.transferTimeout();
    }

    @Override
    public boolean exists(String thumbnailKey) {
        requireDerived(thumbnailKey);
        try {
            s3Client.headObject(HeadObjectRequest.builder().bucket(bucket).key(thumbnailKey).build());
            return true;
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                return false;
            }
            throw e;
        }
    }

    // 사진 확인용 기본 한도(1초)로는 수 MB 원본을 받지 못해 전송용 한도를 따로 건다.
    @Override
    public void download(String originalKey, Path destination) {
        if (originalKey == null || !originalKey.startsWith(travelRecordsDirectory) || originalKey.contains("..")) {
            throw new IllegalArgumentException("not an original photo key: " + originalKey);
        }
        s3Client.getObject(GetObjectRequest.builder()
                        .bucket(bucket)
                        .key(originalKey)
                        .overrideConfiguration(override -> override
                                .apiCallAttemptTimeout(transferTimeout)
                                .apiCallTimeout(transferTimeout.multipliedBy(2)))
                        .build(),
                ResponseTransformer.toFile(destination));
    }

    @Override
    public void upload(Path source, String thumbnailKey) {
        requireDerived(thumbnailKey);
        s3Client.putObject(PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(thumbnailKey)
                        .contentType(CONTENT_TYPE)
                        .cacheControl(CACHE_CONTROL)
                        .overrideConfiguration(override -> override
                                .apiCallAttemptTimeout(transferTimeout)
                                .apiCallTimeout(transferTimeout.multipliedBy(2)))
                        .build(),
                RequestBody.fromFile(source));
    }

    @Override
    public void delete(String thumbnailKey) {
        requireDerived(thumbnailKey);
        List<String> failed = objectDeleter.delete(List.of(thumbnailKey));
        if (!failed.isEmpty()) {
            throw new ThumbnailStorageException("cannot delete thumbnail: " + thumbnailKey);
        }
    }

    private void requireDerived(String thumbnailKey) {
        if (!derivedObjectKeys.isDerived(thumbnailKey)) {
            throw new IllegalArgumentException("not a derived object key: " + thumbnailKey);
        }
    }
}
