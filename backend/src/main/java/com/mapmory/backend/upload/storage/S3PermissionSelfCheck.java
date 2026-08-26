package com.mapmory.backend.upload.storage;

import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.S3Object;

/**
 * 기동 직후 S3 조회 권한을 확인한다. (ADR 0016)
 *
 * 실존 검증은 로컬·CI에서 UploadedObjectChecker를 대역으로 대체해 돌기 때문에, IAM 권한이
 * 빠져 있어도 테스트로는 드러나지 않는다. 그대로 두면 스테이징이나 운영에서 사진이 붙은
 * 기록 저장이 처음 실패할 때야 알게 된다.
 *
 * 확인해야 할 권한이 둘이고, 하나로는 다른 하나를 증명하지 못한다.
 *
 *   - s3:ListBucket — 없으면 "없는 객체"에 404 대신 403이 와서, 400이어야 할 응답이 503이 된다
 *   - s3:GetObject  — 없으면 "있는 객체" 조회가 403이 되어 기록 저장이 전부 실패한다
 *
 * 없는 키만 조회해서는 안 된다. ListBucket만 있으면 GetObject 유무와 무관하게 404가 오므로,
 * 정작 더 흔한 실패(업로드 전용 역할이라 PutObject만 있는 경우)를 놓친다.
 * 그래서 실제로 있는 객체를 하나 찾아 조회한다.
 *
 * 기동을 실패시키지는 않는다. S3 일시 장애로 배포가 막히는 쪽이 더 나쁘다.
 */
@Component
@ConditionalOnProperty(
        name = "upload.storage.s3.permission-self-check",
        havingValue = "true",
        matchIfMissing = true
)
public class S3PermissionSelfCheck {

    private static final Logger log = LoggerFactory.getLogger(S3PermissionSelfCheck.class);
    private static final int FORBIDDEN = 403;
    private static final String TRAVEL_RECORDS = "travel-records/";

    private final S3Client s3Client;
    private final String bucket;
    private final String keyPrefix;

    public S3PermissionSelfCheck(
            S3Client s3Client,
            S3StorageProperties properties
    ) {
        this.s3Client = s3Client;
        this.bucket = properties.bucket();
        this.keyPrefix = properties.normalizedKeyPrefix();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void verifyLookupPermissions() {
        Optional<String> objectKey;
        try {
            objectKey = findAnyObjectKey();
        } catch (S3Exception exception) {
            report(exception, "s3:ListBucket", "없는 사진과 저장소 장애를 구분하지 못해 "
                    + "업로드되지 않은 사진이 400 대신 503으로 거절됩니다");
            return;
        } catch (SdkException exception) {
            unknown(exception);
            return;
        }

        if (objectKey.isEmpty()) {
            log.atInfo()
                    .addKeyValue("event", "S3_PERMISSION_PARTIALLY_VERIFIED")
                    .log("s3:ListBucket 은 확인됐습니다. 저장된 객체가 없어 s3:GetObject 는 "
                            + "확인하지 못했습니다. 사진이 올라간 뒤 다시 기동하면 확인됩니다.");
            return;
        }

        try {
            s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(bucket)
                    .key(objectKey.get())
                    .build());
            log.atInfo()
                    .addKeyValue("event", "S3_PERMISSION_OK")
                    .log("S3 객체 조회 권한 확인됨 (s3:ListBucket, s3:GetObject)");
        } catch (S3Exception exception) {
            report(exception, "s3:GetObject", "사진이 붙은 기록 저장이 모두 실패합니다");
        } catch (SdkException exception) {
            unknown(exception);
        }
    }

    /**
     * 우리 prefix 아래에서 아무 객체나 하나 찾는다. 공용 버킷이므로 다른 팀 경로는 보지 않는다.
     */
    private Optional<String> findAnyObjectKey() {
        ListObjectsV2Response response = s3Client.listObjectsV2(ListObjectsV2Request.builder()
                .bucket(bucket)
                .prefix(travelRecordsPrefix())
                .maxKeys(1)
                .build());
        return response.contents().stream()
                .findFirst()
                .map(S3Object::key);
    }

    private String travelRecordsPrefix() {
        if (keyPrefix.isEmpty()) {
            return TRAVEL_RECORDS;
        }
        return keyPrefix + "/" + TRAVEL_RECORDS;
    }

    private void report(S3Exception exception, String permission, String consequence) {
        if (exception.statusCode() != FORBIDDEN) {
            unknown(exception);
            return;
        }
        log.atError()
                .addKeyValue("event", "S3_PERMISSION_DENIED")
                .addKeyValue("permission", permission)
                .setCause(exception)
                .log("인스턴스 역할에 {} 권한이 없습니다. {}", permission, consequence);
    }

    private void unknown(SdkException exception) {
        log.atWarn()
                .addKeyValue("event", "S3_PERMISSION_UNKNOWN")
                .setCause(exception)
                .log("S3 조회 권한을 확인하지 못했습니다. 저장소가 응답하지 않아 판정을 보류합니다.");
    }
}
