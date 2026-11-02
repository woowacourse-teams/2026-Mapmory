package com.mapmory.backend.place.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mapmory.backend.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class PlaceIdTest {

    private static final int MAX_LENGTH = 255;

    @Test
    void 제공자가_붙인_ID를_그대로_받는다() {
        assertThat(PlaceId.from("ChIJ-gyeong_1").value()).isEqualTo("ChIJ-gyeong_1");
    }

    @Test
    void 저장_한계_길이는_허용한다() {
        assertThatCode(() -> PlaceId.from("a".repeat(MAX_LENGTH))).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "../places", "ChIJ gyeong", "경복궁"})
    void URL_경로에_넣을_수_없는_ID를_거부한다(String value) {
        assertInvalidPlaceId(value);
    }

    @Test
    void 저장_한계를_넘는_ID를_거부한다() {
        assertInvalidPlaceId("a".repeat(MAX_LENGTH + 1));
    }

    private static void assertInvalidPlaceId(String value) {
        assertThat(PlaceId.isValid(value)).isFalse();
        assertThatThrownBy(() -> PlaceId.from(value))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode().code())
                        .isEqualTo("INVALID_PLACE_ID"));
    }
}
