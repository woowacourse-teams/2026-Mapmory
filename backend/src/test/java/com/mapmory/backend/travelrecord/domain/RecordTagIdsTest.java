package com.mapmory.backend.travelrecord.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mapmory.backend.common.exception.BusinessException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;

class RecordTagIdsTest {

    @Test
    void 태그를_다섯_개까지_붙일_수_있다() {
        RecordTagIds tagIds = RecordTagIds.from(ids(5));

        assertThat(tagIds.values()).containsExactly(1L, 2L, 3L, 4L, 5L);
    }

    @Test
    void 태그가_다섯_개를_넘으면_거부한다() {
        assertError(() -> RecordTagIds.from(ids(6)), "TOO_MANY_TAGS");
    }

    @Test
    void 같은_태그를_두_번_붙이면_거부한다() {
        assertError(() -> RecordTagIds.from(List.of(1L, 1L)), "VALIDATION_ERROR");
    }

    @Test
    void null인_태그_ID는_거부한다() {
        assertError(() -> RecordTagIds.from(Arrays.asList(1L, null)), "VALIDATION_ERROR");
    }

    @Test
    void 태그_목록이_없으면_빈_목록으로_본다() {
        assertThat(RecordTagIds.from(null).isEmpty()).isTrue();
    }

    @Test
    void 요청한_순서를_지킨다() {
        assertThat(RecordTagIds.from(List.of(3L, 1L, 2L)).values()).containsExactly(3L, 1L, 2L);
    }

    private List<Long> ids(int count) {
        return LongStream.rangeClosed(1, count).boxed().toList();
    }

    private void assertError(Runnable action, String code) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode().code())
                .isEqualTo(code);
    }
}
