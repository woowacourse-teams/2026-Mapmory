package com.mapmory.backend.travelrecord;

import com.mapmory.backend.common.exception.BusinessException;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * S3에 저장된 미디어 객체의 키. 비어 있을 수 없고 저장 한계를 넘을 수 없다.
 *
 * <p>키의 생성 형식({keyPrefix}/travel-records/{memberId}/{UUID}.{ext})은 업로드 모듈이 정하므로
 * 여기서는 검증하지 않는다. 형식은 설정에 따라 달라지지만 이 두 규칙은 항상 참이다.
 */
@Embeddable
record ObjectKey(
        @Column(name = "object_key", nullable = false, unique = true, length = 500)
        String value
) {
    private static final int MAX_LENGTH = 500;

    ObjectKey {
        validateNotBlank(value);
        validateLength(value);
    }

    static ObjectKey from(String rawObjectKey) {
        return new ObjectKey(rawObjectKey);
    }

    private static void validateNotBlank(String value) {
        if (value == null || value.isBlank()) {
            throwInvalidObjectKey();
        }
    }

    private static void validateLength(String value) {
        if (value.length() > MAX_LENGTH) {
            throwInvalidObjectKey();
        }
    }

    private static void throwInvalidObjectKey() {
        throw new BusinessException(TravelRecordErrorCode.INVALID_OBJECT_KEY);
    }
}
