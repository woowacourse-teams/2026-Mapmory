package com.mapmory.backend.travelrecord;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mapmory.backend.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class ObjectKeyTest {

    private static final int MAX_LENGTH = 500;

    @Test
    void 업로드_모듈이_만든_키를_그대로_받는다() {
        ObjectKey objectKey = ObjectKey.from("mapmory/travel-records/10/uuid.jpg");

        assertThat(objectKey.value()).isEqualTo("mapmory/travel-records/10/uuid.jpg");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void 비어_있는_키를_거부한다(String rawObjectKey) {
        assertInvalidObjectKey(() -> ObjectKey.from(rawObjectKey));
    }

    @Test
    void 저장_한계_길이는_허용한다() {
        assertThatCode(() -> ObjectKey.from("a".repeat(MAX_LENGTH))).doesNotThrowAnyException();
    }

    @Test
    void 저장_한계를_넘는_키를_거부한다() {
        assertInvalidObjectKey(() -> ObjectKey.from("a".repeat(MAX_LENGTH + 1)));
    }

    private void assertInvalidObjectKey(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode().code())
                .isEqualTo("INVALID_OBJECT_KEY");
    }
}
