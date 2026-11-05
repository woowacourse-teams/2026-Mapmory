package com.mapmory.backend.upload.storage;

import com.mapmory.backend.upload.service.port.ObjectDeleter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.Delete;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectsResponse;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import software.amazon.awssdk.services.s3.model.S3Error;

/**
 * S3 객체를 DeleteObjects로 지운다.
 *
 * 공용 버킷이고 지운 객체는 되돌릴 수 없으므로, 우리 prefix 밖의 키가 하나라도 있으면 호출하기 전에 거절한다.
 * DeleteObjects는 한 번에 1000개까지 받고, 권한 부족 같은 키별 실패도 200 응답에 담아 준다.
 */
@Component
public class S3ObjectDeleter implements ObjectDeleter {

    private static final Logger log = LoggerFactory.getLogger(S3ObjectDeleter.class);
    private static final int MAX_KEYS_PER_REQUEST = 1000;

    private final S3Client s3Client;
    private final String bucket;
    private final String ownPrefix;
    private final S3StorageProperties.Client client;

    public S3ObjectDeleter(S3Client s3Client, S3StorageProperties properties) {
        this.s3Client = s3Client;
        this.bucket = properties.bucket();
        this.ownPrefix = properties.keyPrefix() + "/";
        this.client = properties.client();
    }

    @Override
    public List<String> delete(Collection<String> objectKeys) {
        List<String> keys = objectKeys.stream().distinct().toList();
        keys.forEach(this::requireOwnKey);

        List<String> failed = new ArrayList<>();
        for (int start = 0; start < keys.size(); start += MAX_KEYS_PER_REQUEST) {
            int end = Math.min(start + MAX_KEYS_PER_REQUEST, keys.size());
            failed.addAll(deleteBatch(keys.subList(start, end)));
        }
        return failed;
    }

    private void requireOwnKey(String key) {
        if (!key.startsWith(ownPrefix)) {
            throw new IllegalArgumentException("우리 경로 밖의 객체는 지울 수 없습니다. key=" + key);
        }
    }

    private List<String> deleteBatch(List<String> keys) {
        try {
            DeleteObjectsResponse response = s3Client.deleteObjects(request(keys));
            if (response.hasErrors()) {
                logFailure(response.errors().size(), response.errors().getFirst().code(), null);
            }
            return response.errors().stream().map(S3Error::key).toList();
        } catch (SdkException exception) {
            logFailure(keys.size(), null, exception);
            return keys;
        }
    }

    private DeleteObjectsRequest request(List<String> keys) {
        List<ObjectIdentifier> objects = keys.stream()
                .map(key -> ObjectIdentifier.builder().key(key).build())
                .toList();
        return DeleteObjectsRequest.builder()
                .bucket(bucket)
                .delete(Delete.builder().objects(objects).quiet(true).build())
                .overrideConfiguration(override -> override
                        .apiCallAttemptTimeout(client.deleteAttemptTimeout())
                        .apiCallTimeout(client.deleteTotalTimeout()))
                .build();
    }

    private void logFailure(int count, String errorCode, SdkException cause) {
        log.atWarn()
                .addKeyValue("event", "S3_OBJECT_DELETE_FAILED")
                .addKeyValue("failedKeys", count)
                .addKeyValue("errorCode", errorCode)
                .setCause(cause)
                .log("S3 객체를 지우지 못했다");
    }
}
