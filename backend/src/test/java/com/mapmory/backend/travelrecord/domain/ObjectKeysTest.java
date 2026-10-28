package com.mapmory.backend.travelrecord.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mapmory.backend.common.exception.BusinessException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class ObjectKeysTest {

    private static final String KEY_A = "travel-records/10/a.jpg";
    private static final String KEY_B = "travel-records/10/b.jpg";
    private static final String KEY_C = "travel-records/10/c.jpg";

    @Test
    void 요청한_순서를_지킨다() {
        assertThat(ObjectKeys.from(List.of(KEY_C, KEY_A, KEY_B)).values()).containsExactly(KEY_C, KEY_A, KEY_B);
    }

    @ParameterizedTest
    @NullAndEmptySource
    void 사진이_없으면_거부한다(List<String> none) {
        assertError(() -> ObjectKeys.from(none), "PHOTO_REQUIRED");
    }

    @Test
    void 사진은_한_장부터_백_장까지_받는다() {
        assertThat(ObjectKeys.from(List.of(KEY_A)).values()).hasSize(1);
        assertThat(ObjectKeys.from(keys(100)).values()).hasSize(100);
    }

    @Test
    void 사진이_백_장을_넘으면_거부한다() {
        assertError(() -> ObjectKeys.from(keys(101)), "TOO_MANY_PHOTOS");
    }

    @Test
    void 중복을_포함한_요청_그대로_장수를_센다() {
        // 장수 검사를 지나 중복 검사에서 걸린다.
        assertInvalid(() -> ObjectKeys.from(List.of(KEY_A, KEY_A)));
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

    private List<String> keys(int count) {
        return IntStream.rangeClosed(1, count)
                .mapToObj(i -> "travel-records/10/" + i + ".jpg")
                .toList();
    }

    private void assertInvalid(Runnable action) {
        assertError(action, "INVALID_OBJECT_KEY");
    }

    private void assertError(Runnable action, String code) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode().code())
                .isEqualTo(code);
    }
}
