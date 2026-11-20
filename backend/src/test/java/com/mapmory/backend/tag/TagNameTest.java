package com.mapmory.backend.tag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mapmory.backend.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

class TagNameTest {

    @Test
    void 표시_이름과_중복_비교_키를_만든다() {
        TagName tagName = TagName.from("  Date   Course  ");

        assertThat(tagName.displayName()).isEqualTo("Date Course");
        assertThat(tagName.nameKey()).isEqualTo("date course");
    }

    @Test
    void 표시_이름을_NFC로_정규화한다() {
        TagName tagName = TagName.from("가");

        assertThat(tagName.displayName()).isEqualTo("가");
    }

    @Test
    void 소문자_변환으로_비교_키가_늘어나도_30자_표시_이름을_허용한다() {
        TagName tagName = TagName.from("İ".repeat(30));

        assertThat(tagName.displayName()).hasSize(30);
        assertThat(tagName.nameKey().codePointCount(0, tagName.nameKey().length())).isEqualTo(60);
    }

    @Test
    void 유효하지_않은_이름을_거부한다() {
        assertInvalidName("   ");
        assertInvalidName("#연인");
        assertInvalidName("친구\u0000");
        assertInvalidName("가".repeat(31));
    }

    @Test
    void 표시_이름과_비교_키가_일치하지_않는_직접_생성을_거부한다() {
        assertThatThrownBy(() -> new TagName("친구", "friend"))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(TagErrorCode.INVALID_TAG_NAME);
    }

    @Test
    void null_이름은_서버_오류가_아니라_잘못된_이름으로_거절한다() {
        assertInvalidName(null);
    }

    @Test
    void 저장된_값으로_만들_때_표시_이름이나_비교_키가_null이면_거부한다() {
        assertInvalidTagName(() -> new TagName(null, "친구"));
        assertInvalidTagName(() -> new TagName("친구", null));
    }

    @Test
    void 저장된_값으로_만들_때_정규화되지_않은_표시_이름을_거부한다() {
        assertInvalidTagName(() -> new TagName("데이트  코스", "데이트  코스"));
    }

    private void assertInvalidTagName(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(TagErrorCode.INVALID_TAG_NAME);
    }

    private void assertInvalidName(String rawName) {
        assertThatThrownBy(() -> TagName.from(rawName))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(TagErrorCode.INVALID_TAG_NAME);
    }
}
