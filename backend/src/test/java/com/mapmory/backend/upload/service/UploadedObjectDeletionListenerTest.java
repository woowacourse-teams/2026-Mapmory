package com.mapmory.backend.upload.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.mapmory.backend.upload.storage.UploadedObjectDeleter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import org.junit.jupiter.api.Test;

class UploadedObjectDeletionListenerTest {

    private final UploadedObjectDeleter uploadedObjectDeleter = mock(UploadedObjectDeleter.class);
    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final UploadedObjectDeletionListener listener = new UploadedObjectDeletionListener(
            uploadedObjectDeleter,
            meterRegistry
    );

    @Test
    void 요청된_객체를_삭제하고_성공_개수를_기록한다() {
        List<String> objectKeys = List.of("mapmory/travel-records/10/a.jpg");

        listener.deleteAfterCommit(new UploadedObjectDeletionRequested(objectKeys));

        verify(uploadedObjectDeleter).deleteAll(objectKeys);
        assertThat(deletionCount("SUCCESS")).isEqualTo(1.0);
    }

    @Test
    void 삭제가_실패해도_예외를_전파하지_않고_실패_개수를_기록한다() {
        List<String> objectKeys = List.of(
                "mapmory/travel-records/10/a.jpg",
                "mapmory/travel-records/10/b.jpg"
        );
        doThrow(new IllegalStateException("S3 unavailable"))
                .when(uploadedObjectDeleter).deleteAll(objectKeys);

        assertThatCode(() -> listener.deleteAfterCommit(new UploadedObjectDeletionRequested(objectKeys)))
                .doesNotThrowAnyException();
        assertThat(deletionCount("FAILURE")).isEqualTo(2.0);
    }

    private double deletionCount(String outcome) {
        return meterRegistry.get(UploadedObjectDeletionListener.METRIC_NAME)
                .tag("outcome", outcome)
                .counter()
                .count();
    }
}
