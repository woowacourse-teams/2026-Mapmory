package com.mapmory.backend.upload.service;

import com.mapmory.backend.upload.storage.UploadedObjectDeleter;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class UploadedObjectDeletionListener {

    public static final String METRIC_NAME = "mapmory.upload.object.deletion";

    private static final Logger log = LoggerFactory.getLogger(UploadedObjectDeletionListener.class);

    private final UploadedObjectDeleter uploadedObjectDeleter;
    private final MeterRegistry meterRegistry;

    public UploadedObjectDeletionListener(
            UploadedObjectDeleter uploadedObjectDeleter,
            MeterRegistry meterRegistry
    ) {
        this.uploadedObjectDeleter = uploadedObjectDeleter;
        this.meterRegistry = meterRegistry;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void deleteAfterCommit(UploadedObjectDeletionRequested event) {
        List<String> objectKeys = event.objectKeys();
        try {
            uploadedObjectDeleter.deleteAll(objectKeys);
            deletionCounter("SUCCESS").increment(objectKeys.size());
        } catch (RuntimeException exception) {
            deletionCounter("FAILURE").increment(objectKeys.size());
            log.atError()
                    .addKeyValue("event", "S3_OBJECT_DELETE_FAILED")
                    .addKeyValue("objectCount", objectKeys.size())
                    .setCause(exception)
                    .log("Failed to delete uploaded objects after transaction commit");
        }
    }

    private Counter deletionCounter(String outcome) {
        return Counter.builder(METRIC_NAME)
                .description("Number of uploaded S3 objects requested for deletion")
                .tag("outcome", outcome)
                .register(meterRegistry);
    }
}
