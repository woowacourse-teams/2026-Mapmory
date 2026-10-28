package com.mapmory.backend.travelrecord.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mapmory.backend.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class TravelTitleTest {

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n"})
    void 제목이_없거나_공백뿐이면_빈_제목이다(String raw) {
        assertThat(TravelTitle.from(raw).value()).isEmpty();
    }

    @Test
    void 앞뒤_공백은_자르지_않는다() {
        assertThat(TravelTitle.from("  제주 여행 ").value()).isEqualTo("  제주 여행 ");
    }

    @Test
    void 제목은_200자까지_받는다() {
        assertThat(TravelTitle.from("가".repeat(200)).value()).hasSize(200);
    }

    @Test
    void 제목이_200자를_넘으면_거부한다() {
        assertThatThrownBy(() -> TravelTitle.from("가".repeat(201)))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode().code())
                .isEqualTo("VALIDATION_ERROR");
    }

    @Test
    void 길이는_앱과_같게_UTF16_글자_수로_센다() {
        // 이모지 하나는 UTF-16으로 2글자다. 100개면 200글자로 받고, 101개면 거부한다.
        String emoji = "🌊";

        assertThat(TravelTitle.from(emoji.repeat(100)).value()).hasSize(200);
        assertThatThrownBy(() -> TravelTitle.from(emoji.repeat(101))).isInstanceOf(BusinessException.class);
    }
}
