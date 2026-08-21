package com.mapmory.backend.tag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mapmory.backend.common.exception.BusinessException;
import com.mapmory.backend.member.Member;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class TagTest {

    private final Member member = Mockito.mock(Member.class);

    @Test
    void 생성할_때_이름을_정규화한다() {
        Tag tag = Tag.of(member, "  Date   Course  ");

        assertThat(tag.getName()).isEqualTo("Date Course");
        assertThat(tag.getNameKey()).isEqualTo("date course");
    }

    @Test
    void 이름을_바꿀_때도_정규화한다() {
        Tag tag = Tag.of(member, "친구");

        tag.rename("  RAMEN   PLACE  ");

        assertThat(tag.getName()).isEqualTo("RAMEN PLACE");
        assertThat(tag.getNameKey()).isEqualTo("ramen place");
    }

    @Test
    void 유효하지_않은_이름으로는_생성할_수_없다() {
        assertThatThrownBy(() -> Tag.of(member, "#연인"))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(TagErrorCode.INVALID_TAG_NAME);
    }
}
