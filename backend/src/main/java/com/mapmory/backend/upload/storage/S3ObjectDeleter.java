package com.mapmory.backend.upload.storage;

import com.mapmory.backend.upload.service.port.ObjectDeleter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.Delete;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectsResponse;
import software.amazon.awssdk.services.s3.model.ListObjectVersionsRequest;
import software.amazon.awssdk.services.s3.model.ListObjectVersionsResponse;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import software.amazon.awssdk.services.s3.model.S3Error;

/**
 * S3 객체를 모든 버전까지 영구히 지운다. 지운 객체는 되돌릴 수 없다.
 *
 * 버킷은 버전 관리가 켜져 있어, 버전 없이 지우면 삭제 마커만 붙고 원본은 이전 버전으로 남는다.
 * 그래서 키마다 버전과 삭제 마커를 조회해 버전을 지정해서 지운다. 인스턴스 역할에 s3:ListBucketVersions, s3:DeleteObjectVersion이 필요하다.
 *
 * 공용 버킷이므로 우리 prefix 밖의 키가 하나라도 있으면 호출하기 전에 거절한다.
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

        Set<String> failed = new LinkedHashSet<>();
        List<ObjectIdentifier> versions = new ArrayList<>();
        for (String key : keys) {
            try {
                versions.addAll(versionsOf(key));
            } catch (SdkException exception) {
                logFailure(1, null, exception);
                failed.add(key);
            }
        }
        for (int start = 0; start < versions.size(); start += MAX_KEYS_PER_REQUEST) {
            int end = Math.min(start + MAX_KEYS_PER_REQUEST, versions.size());
            failed.addAll(deleteBatch(versions.subList(start, end)));
        }
        return List.copyOf(failed);
    }

    @Override
    public boolean owns(String objectKey) {
        return objectKey.startsWith(ownPrefix);
    }

    private void requireOwnKey(String key) {
        if (!owns(key)) {
            throw new IllegalArgumentException("우리 경로 밖의 객체는 지울 수 없습니다. key=" + key);
        }
    }

    // 조회는 prefix로 찾으므로 a.jpg를 찾으면 a.jpg.bak도 나온다. 키가 정확히 같은 것만 지운다.
    private List<ObjectIdentifier> versionsOf(String key) {
        List<ObjectIdentifier> versions = new ArrayList<>();
        String keyMarker = null;
        String versionIdMarker = null;
        ListObjectVersionsResponse response;
        do {
            response = s3Client.listObjectVersions(listRequest(key, keyMarker, versionIdMarker));
            response.versions().stream()
                    .filter(version -> version.key().equals(key))
                    .forEach(version -> versions.add(identifier(key, version.versionId())));
            response.deleteMarkers().stream()
                    .filter(marker -> marker.key().equals(key))
                    .forEach(marker -> versions.add(identifier(key, marker.versionId())));
            keyMarker = response.nextKeyMarker();
            versionIdMarker = response.nextVersionIdMarker();
        } while (Boolean.TRUE.equals(response.isTruncated()));
        return versions;
    }

    private ListObjectVersionsRequest listRequest(String key, String keyMarker, String versionIdMarker) {
        return ListObjectVersionsRequest.builder()
                .bucket(bucket)
                .prefix(key)
                .keyMarker(keyMarker)
                .versionIdMarker(versionIdMarker)
                .overrideConfiguration(override -> override
                        .apiCallAttemptTimeout(client.deleteAttemptTimeout())
                        .apiCallTimeout(client.deleteTotalTimeout()))
                .build();
    }

    private ObjectIdentifier identifier(String key, String versionId) {
        return ObjectIdentifier.builder().key(key).versionId(versionId).build();
    }

    private List<String> deleteBatch(List<ObjectIdentifier> versions) {
        try {
            DeleteObjectsResponse response = s3Client.deleteObjects(deleteRequest(versions));
            if (response.hasErrors()) {
                logFailure(response.errors().size(), response.errors().getFirst().code(), null);
            }
            return response.errors().stream().map(S3Error::key).toList();
        } catch (SdkException exception) {
            logFailure(versions.size(), null, exception);
            return versions.stream().map(ObjectIdentifier::key).toList();
        }
    }

    private DeleteObjectsRequest deleteRequest(List<ObjectIdentifier> versions) {
        return DeleteObjectsRequest.builder()
                .bucket(bucket)
                .delete(Delete.builder().objects(versions).quiet(true).build())
                .overrideConfiguration(override -> override
                        .apiCallAttemptTimeout(client.deleteAttemptTimeout())
                        .apiCallTimeout(client.deleteTotalTimeout()))
                .build();
    }

    private void logFailure(int count, String errorCode, SdkException cause) {
        log.atWarn()
                .addKeyValue("event", "S3_OBJECT_DELETE_FAILED")
                .addKeyValue("failedCount", count)
                .addKeyValue("errorCode", errorCode)
                .setCause(cause)
                .log("S3 객체를 지우지 못했다");
    }
}
