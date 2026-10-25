package com.mapmory.backend.upload.service;

import com.mapmory.backend.common.exception.BusinessException;
import com.mapmory.backend.common.monitoring.MonitoredOperation;
import com.mapmory.backend.common.monitoring.OperationTimer;
import com.mapmory.backend.upload.UploadErrorCode;
import com.mapmory.backend.upload.storage.UploadedObjectChecker;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
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
