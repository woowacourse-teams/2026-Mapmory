package com.mapmory.backend.upload.service;

import com.mapmory.backend.common.exception.BusinessException;
import com.mapmory.backend.common.monitoring.MonitoredOperation;
import com.mapmory.backend.common.monitoring.OperationTimer;
import com.mapmory.backend.upload.UploadErrorCode;
import com.mapmory.backend.upload.storage.UploadedObjectChecker;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 기록에 붙이려는 객체가 실제로 업로드되었는지 확인한다. (ADR 0016)
 *
 * 하나라도 없으면 저장 전체를 거절한다. 10장 중 9장만 붙은 기록은 사용자가 의도한 결과가
 * 아니고, 어느 장이 빠졌는지 앱이 알고 다시 올리게 하는 편이 낫다.
 */
@Service
public class UploadedObjectVerifier {

    private final UploadedObjectChecker uploadedObjectChecker;
    private final OperationTimer operationTimer;

    public UploadedObjectVerifier(
            UploadedObjectChecker uploadedObjectChecker,
            OperationTimer operationTimer
    ) {
        this.uploadedObjectChecker = uploadedObjectChecker;
        this.operationTimer = operationTimer;
    }

    /**
     * 순차로 확인한다. 10장이면 100~300ms 수준이라, 실행기를 들이는 복잡도를 아직 사지 않는다.
     * p95가 문제가 되면 그때 병렬로 바꾼다. (ADR 0014의 계측 규칙을 따른다)
     */
    public void verifyAllUploaded(List<String> objectKeys) {
        if (objectKeys.isEmpty()) {
            return;
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
