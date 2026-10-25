package com.mapmory.backend.upload.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mapmory.backend.common.exception.BusinessException;
import com.mapmory.backend.common.monitoring.OperationTimer;
import com.mapmory.backend.upload.UploadErrorCode;
import com.mapmory.backend.upload.service.port.UploadedObjectChecker;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class UploadedObjectVerifierTest {

    private final UploadedObjectChecker uploadedObjectChecker = mock(UploadedObjectChecker.class);
    private final UploadedObjectVerifier verifier = new UploadedObjectVerifier(
            uploadedObjectChecker,
            new OperationTimer(new SimpleMeterRegistry())
    );

    @Test
    void 모든_객체가_있으면_순서대로_확인한다() {
        when(uploadedObjectChecker.exists("a.jpg")).thenReturn(true);
        when(uploadedObjectChecker.exists("b.jpg")).thenReturn(true);

        verifier.verifyAllUploaded(List.of("a.jpg", "b.jpg"));

        InOrder order = inOrder(uploadedObjectChecker);
        order.verify(uploadedObjectChecker).exists("a.jpg");
        order.verify(uploadedObjectChecker).exists("b.jpg");
    }

    @Test
    void 하나라도_없으면_나머지를_확인하지_않고_거절한다() {
        when(uploadedObjectChecker.exists("a.jpg")).thenReturn(true);
        when(uploadedObjectChecker.exists("missing.jpg")).thenReturn(false);

        assertThatThrownBy(() -> verifier.verifyAllUploaded(List.of("a.jpg", "missing.jpg", "c.jpg")))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(UploadErrorCode.MEDIA_NOT_UPLOADED);
        verify(uploadedObjectChecker, never()).exists("c.jpg");
    }

    @Test
    void 객체가_없으면_저장소를_호출하지_않는다() {
        verifier.verifyAllUploaded(List.of());

        verify(uploadedObjectChecker, never()).exists(org.mockito.ArgumentMatchers.anyString());
    }
}
