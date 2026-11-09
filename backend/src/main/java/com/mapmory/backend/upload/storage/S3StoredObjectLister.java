package com.mapmory.backend.upload.storage;

import com.mapmory.backend.upload.service.port.StoredObject;
import com.mapmory.backend.upload.service.port.StoredObjectLister;
import com.mapmory.backend.upload.service.port.StoredObjectPage;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;

/**
 * 여행 기록 경로({key-prefix}/travel-records/) 아래 객체를 ListObjectsV2로 1000개씩 읽는다. (#186)
 *
 * <p>공용 버킷이라 우리 경로 밖은 읽지 않는다. 필요한 권한은 기동 때 {@link S3PermissionSelfCheck}가 같은 경로로 확인하는 s3:ListBucket이다.
 * 현재 버전만 보이므로 현재 버전이 삭제 마커인 키의 이전 버전은 나오지 않는다.
 * 배치에서만 부르고 1000개를 받으므로 삭제와 같은 시간 한도를 쓴다.
 */
@Component
public class S3StoredObjectLister implements StoredObjectLister {

    private static final int MAX_KEYS_PER_PAGE = 1000;

    private final S3Client s3Client;
    private final String bucket;
    private final String travelRecordsDirectory;
    private final S3StorageProperties.Client client;

    public S3StoredObjectLister(S3Client s3Client, S3StorageProperties properties) {
        this.s3Client = s3Client;
        this.bucket = properties.bucket();
        this.travelRecordsDirectory = properties.travelRecordsDirectory();
        this.client = properties.client();
    }

    @Override
    public StoredObjectPage listTravelRecordObjects(String continuationToken) {
        ListObjectsV2Response response = s3Client.listObjectsV2(ListObjectsV2Request.builder()
                .bucket(bucket)
                .prefix(travelRecordsDirectory)
                .continuationToken(continuationToken)
                .maxKeys(MAX_KEYS_PER_PAGE)
                .overrideConfiguration(override -> override
                        .apiCallAttemptTimeout(client.deleteAttemptTimeout())
                        .apiCallTimeout(client.deleteTotalTimeout()))
                .build());
        String nextToken = Boolean.TRUE.equals(response.isTruncated()) ? response.nextContinuationToken() : null;
        return new StoredObjectPage(
                response.contents().stream()
                        .map(object -> new StoredObject(object.key(), object.lastModified()))
                        .toList(),
                nextToken
        );
    }
}
