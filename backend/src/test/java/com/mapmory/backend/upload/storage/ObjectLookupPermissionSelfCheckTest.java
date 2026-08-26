package com.mapmory.backend.upload.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mapmory.backend.common.exception.BusinessException;
import com.mapmory.backend.upload.UploadErrorCode;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * 권한 누락은 로컬·CI에서 드러나지 않으므로 기동 시점에 한 번 확인한다. (ADR 0016)
 *
 * 확인에 실패해도 기동은 계속되어야 한다. S3 일시 장애로 배포가 막히는 쪽이 더 나쁘다.
 */
class ObjectLookupPermissionSelfCheckTest {

    private final UploadedObjectChecker uploadedObjectChecker = mock(UploadedObjectChecker.class);

    @Test
    void 실제_발급_키와_같은_prefix로_확인한다() {
        ObjectLookupPermissionSelfCheck selfCheck = selfCheck("mapmory");
        when(uploadedObjectChecker.exists(anyString())).thenReturn(false);

        selfCheck.verifyObjectLookupPermission();

        ArgumentCaptor<String> probeKey = ArgumentCaptor.forClass(String.class);
        verify(uploadedObjectChecker).exists(probeKey.capture());
        assertThat(probeKey.getValue()).startsWith("mapmory/travel-records/");
    }

    @Test
    void prefix가_없으면_붙이지_않는다() {
        ObjectLookupPermissionSelfCheck selfCheck = selfCheck("");
        when(uploadedObjectChecker.exists(anyString())).thenReturn(false);

        selfCheck.verifyObjectLookupPermission();

        ArgumentCaptor<String> probeKey = ArgumentCaptor.forClass(String.class);
        verify(uploadedObjectChecker).exists(probeKey.capture());
        assertThat(probeKey.getValue()).startsWith("travel-records/");
    }

    @Test
    void 접근이_거부되어도_기동을_막지_않는다() {
        ObjectLookupPermissionSelfCheck selfCheck = selfCheck("mapmory");
        when(uploadedObjectChecker.exists(anyString()))
                .thenThrow(new BusinessException(UploadErrorCode.STORAGE_ACCESS_DENIED));

        assertThatCode(selfCheck::verifyObjectLookupPermission).doesNotThrowAnyException();
    }

    @Test
    void 저장소가_응답하지_않아도_기동을_막지_않는다() {
        ObjectLookupPermissionSelfCheck selfCheck = selfCheck("mapmory");
        when(uploadedObjectChecker.exists(anyString()))
                .thenThrow(new BusinessException(UploadErrorCode.STORAGE_UNAVAILABLE));

        assertThatCode(selfCheck::verifyObjectLookupPermission).doesNotThrowAnyException();
    }

    private ObjectLookupPermissionSelfCheck selfCheck(String keyPrefix) {
        return new ObjectLookupPermissionSelfCheck(
                uploadedObjectChecker,
                new S3StorageProperties("mapmory-test", "ap-northeast-2", keyPrefix)
        );
    }
}
