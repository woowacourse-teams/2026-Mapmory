package com.mapmory.backend.upload.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mapmory.backend.common.exception.BusinessException;
import com.mapmory.backend.common.monitoring.OperationTimer;
import com.mapmory.backend.upload.UploadErrorCode;
import com.mapmory.backend.upload.service.port.UploadedObjectChecker;
import com.mapmory.backend.upload.storage.S3ObjectKeyGenerator;
import com.mapmory.backend.upload.storage.S3StorageProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InOrder;

class UploadedObjectVerifierTest {

    private static final Long MEMBER_ID = 10L;
    private static final String A = "mapmory/travel-records/10/a.jpg";
    private static final String B = "mapmory/travel-records/10/b.jpg";
    private static final String C = "mapmory/travel-records/10/c.jpg";

    private final UploadedObjectChecker uploadedObjectChecker = mock(UploadedObjectChecker.class);
    private final UploadedObjectVerifier verifier = new UploadedObjectVerifier(
            new S3ObjectKeyGenerator(new S3StorageProperties(
                    "mapmory-test", "ap-northeast-2", "mapmory", Duration.ofMinutes(5), null)),
            uploadedObjectChecker,
            new OperationTimer(new SimpleMeterRegistry())
    );

    @Test
    void 모든_객체가_있으면_순서대로_확인한다() {
        when(uploadedObjectChecker.exists(A)).thenReturn(true);
        when(uploadedObjectChecker.exists(B)).thenReturn(true);

        verifier.verifyAllUploaded(MEMBER_ID, List.of(A, B));

        InOrder order = inOrder(uploadedObjectChecker);
        order.verify(uploadedObjectChecker).exists(A);
        order.verify(uploadedObjectChecker).exists(B);
    }

    @Test
    void 하나라도_없으면_나머지를_확인하지_않고_거절한다() {
        String missing = "mapmory/travel-records/10/missing.jpg";
        when(uploadedObjectChecker.exists(A)).thenReturn(true);
        when(uploadedObjectChecker.exists(missing)).thenReturn(false);

        assertThatThrownBy(() -> verifier.verifyAllUploaded(MEMBER_ID, List.of(A, missing, C)))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(UploadErrorCode.MEDIA_NOT_UPLOADED);
        verify(uploadedObjectChecker, never()).exists(C);
    }

    @Test
    void 객체가_없으면_저장소를_호출하지_않는다() {
        verifier.verifyAllUploaded(MEMBER_ID, List.of());

        verify(uploadedObjectChecker, never()).exists(anyString());
    }

    /**
     * 남의 경로 키는 객체가 있는지 저장소에 묻지 않고, 업로드하지 않은 사진으로 거절한다.
     */
    @ParameterizedTest
    @ValueSource(strings = {
            "mapmory/travel-records/11/a.jpg",
            "mapmory/travel-records/1/a.jpg",
            "mapmory/travel-records/100/a.jpg",
            "mapmory/share-videos/15/travel-timeline.mp4",
            "mapmory/dev/travel-records/10/a.jpg",
            "travel-records/10/a.jpg",
            "other-team/travel-records/10/a.jpg",
            "mapmory/travel-records/10/",
            "mapmory/travel-records/10/../11/a.jpg",
            "mapmory/travel-records/10/nested/a.jpg"
    })
    void 이_회원에게_발급한_키가_아니면_저장소를_부르지_않고_거절한다(String foreignKey) {
        when(uploadedObjectChecker.exists(anyString())).thenReturn(true);

        assertThatThrownBy(() -> verifier.verifyAllUploaded(MEMBER_ID, List.of(A, foreignKey)))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(UploadErrorCode.MEDIA_NOT_UPLOADED);
        verify(uploadedObjectChecker, never()).exists(anyString());
    }
}
