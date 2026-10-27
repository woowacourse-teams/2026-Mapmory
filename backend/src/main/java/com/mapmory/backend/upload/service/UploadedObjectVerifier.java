package com.mapmory.backend.upload.service;

import com.mapmory.backend.common.exception.BusinessException;
import com.mapmory.backend.common.monitoring.MonitoredOperation;
import com.mapmory.backend.common.monitoring.OperationTimer;
import com.mapmory.backend.upload.UploadErrorCode;
import com.mapmory.backend.upload.service.port.ObjectKeyGenerator;
import com.mapmory.backend.upload.service.port.UploadedObjectChecker;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 기록에 붙이려는 키가 이 회원이 올린 객체인지 확인한다. (ADR 0018)
 *
 * 먼저 이 회원에게 발급한 경로의 키인지 보고, 그다음 저장소에 실제로 있는지 본다.
 * 남의 경로 키는 저장소를 부르지 않고 "업로드되지 않은 사진"으로 거절한다.
 * 그 객체가 있는지 없는지를 응답으로 드러내지 않기 위해서다.
 */
@Component
public class UploadedObjectVerifier {

    private final ObjectKeyGenerator objectKeyGenerator;
    private final UploadedObjectChecker uploadedObjectChecker;
    private final OperationTimer operationTimer;

    public UploadedObjectVerifier(
            ObjectKeyGenerator objectKeyGenerator,
            UploadedObjectChecker uploadedObjectChecker,
            OperationTimer operationTimer
    ) {
        this.objectKeyGenerator = objectKeyGenerator;
        this.uploadedObjectChecker = uploadedObjectChecker;
        this.operationTimer = operationTimer;
    }

    public void verifyAllUploaded(Long memberId, List<String> objectKeys) {
        if (objectKeys.isEmpty()) {
            return;
        }
        for (String objectKey : objectKeys) {
            if (!objectKeyGenerator.isIssuedTo(memberId, objectKey)) {
                throw new BusinessException(UploadErrorCode.MEDIA_NOT_UPLOADED);
            }
        }

        operationTimer.record(MonitoredOperation.MEDIA_EXISTENCE_CHECK, () -> {
            for (String objectKey : objectKeys) {
                if (!uploadedObjectChecker.exists(objectKey)) {
                    throw new BusinessException(UploadErrorCode.MEDIA_NOT_UPLOADED);
                }
            }
            return null;
        });
    }
}
