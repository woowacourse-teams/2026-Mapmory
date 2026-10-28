package com.mapmory.backend.travelrecord.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mapmory.backend.common.exception.BusinessException;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ObjectKeysTest {

    private static final String KEY_A = "travel-records/10/a.jpg";
    private static final String KEY_B = "travel-records/10/b.jpg";
    private static final String KEY_C = "travel-records/10/c.jpg";

    @Test
    void 요청한_순서를_지킨다() {
        assertThat(ObjectKeys.from(List.of(KEY_C, KEY_A, KEY_B)).values()).containsExactly(KEY_C, KEY_A, KEY_B);
    }

    @Test
    void 목록이_없으면_빈_목록으로_본다() {
        assertThat(ObjectKeys.from(null).isEmpty()).isTrue();
    }

    @Test
    void 한_일지_안에서_Object_Key가_중복되면_거부한다() {
        assertInvalid(() -> ObjectKeys.from(List.of(KEY_A, KEY_B, KEY_A)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " "})
    void 빈_키는_거부한다(String blank) {
        assertInvalid(() -> ObjectKeys.from(List.of(KEY_A, blank)));
    }

    @Test
    void null_키는_거부한다() {
        assertInvalid(() -> ObjectKeys.from(Arrays.asList(KEY_A, null)));
    }

    @Test
    void 저장_한계를_넘는_키는_거부한다() {
        assertInvalid(() -> ObjectKeys.from(List.of("a".repeat(501))));
    }

    @Test
    void 이미_있는_키를_빼고_순서를_지킨다() {
        ObjectKeys requested = ObjectKeys.from(List.of(KEY_C, KEY_A, KEY_B));

        ObjectKeys newcomers = requested.excluding(Set.of(ObjectKey.from(KEY_A)));

        assertThat(newcomers.values()).containsExactly(KEY_C, KEY_B);
    }

    private void assertInvalid(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode().code())
                .isEqualTo("INVALID_OBJECT_KEY");
    }
}
