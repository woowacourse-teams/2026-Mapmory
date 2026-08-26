package com.mapmory.backend.upload.storage;

import com.mapmory.backend.common.exception.BusinessException;
import com.mapmory.backend.upload.UploadErrorCode;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 기동 직후 객체 조회 권한을 한 번 확인한다. (ADR 0016)
 *
 * 실존 검증은 로컬·CI에서 UploadedObjectChecker를 대역으로 대체해 돌기 때문에, IAM 권한이
 * 빠져 있어도 테스트로는 드러나지 않는다. 그대로 두면 스테이징이나 운영에서 사진이 붙은
 * 기록 저장이 처음 실패할 때야 알게 된다.
 *
 * 없을 수밖에 없는 키로 한 번 호출해 응답을 본다. 404가 오면 조회 권한이 정상이고,
 * 403이면 s3:GetObject 또는 s3:ListBucket 이 빠져 있다는 뜻이다.
 *
 * 기동을 실패시키지는 않는다. S3 일시 장애로 배포가 막히는 쪽이 더 나쁘다.
 */
@Component
public class ObjectLookupPermissionSelfCheck {

    private static final Logger log = LoggerFactory.getLogger(ObjectLookupPermissionSelfCheck.class);
    private static final String PROBE_KEY_FORMAT = "travel-records/0/permission-probe-%s";

    private final UploadedObjectChecker uploadedObjectChecker;
    private final String keyPrefix;

    public ObjectLookupPermissionSelfCheck(
            UploadedObjectChecker uploadedObjectChecker,
            S3StorageProperties properties
    ) {
        this.uploadedObjectChecker = uploadedObjectChecker;
        this.keyPrefix = properties.normalizedKeyPrefix();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void verifyObjectLookupPermission() {
        String probeKey = probeKey();
        try {
            uploadedObjectChecker.exists(probeKey);
            log.atInfo()
                    .addKeyValue("event", "S3_OBJECT_LOOKUP_PERMISSION_OK")
                    .log("S3 객체 조회 권한 확인됨");
        } catch (BusinessException exception) {
            if (exception.getErrorCode() == UploadErrorCode.STORAGE_ACCESS_DENIED) {
                log.atError()
                        .addKeyValue("event", "S3_OBJECT_LOOKUP_PERMISSION_DENIED")
                        .setCause(exception)
                        .log("S3 객체 조회 권한이 없습니다. 인스턴스 역할의 s3:GetObject, "
                                + "s3:ListBucket 을 확인하세요. 사진이 붙은 기록 저장이 모두 실패합니다.");
                return;
            }
            log.atWarn()
                    .addKeyValue("event", "S3_OBJECT_LOOKUP_PERMISSION_UNKNOWN")
                    .setCause(exception)
                    .log("S3 객체 조회 권한을 확인하지 못했습니다. 저장소 응답이 없어 판정을 보류합니다.");
        }
    }

    /**
     * 실제 발급 키와 같은 prefix 아래에 둔다. 권한이 prefix 단위로 걸려 있을 수 있어,
     * 다른 경로로 확인하면 엉뚱한 곳의 권한을 보게 된다.
     */
    private String probeKey() {
        String probeKey = PROBE_KEY_FORMAT.formatted(UUID.randomUUID());
        if (keyPrefix.isEmpty()) {
            return probeKey;
        }
        return keyPrefix + "/" + probeKey;
    }
}
