package com.mapmory.backend.travelrecord;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mapmory.backend.common.exception.BusinessException;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class TravelPeriodTest {

    private static final LocalDate START = LocalDate.of(2026, 8, 11);

    @Test
    void 종료일이_없어도_기간을_만든다() {
        TravelPeriod period = TravelPeriod.of(START, null);

        assertThat(period.startDate()).isEqualTo(START);
        assertThat(period.endDate()).isNull();
    }

    @Test
    void 종료일이_시작일과_같으면_기간을_만든다() {
        assertThatCode(() -> TravelPeriod.of(START, START)).doesNotThrowAnyException();
    }

    @Test
    void 시작일이_없으면_거부한다() {
        assertInvalidPeriod(() -> TravelPeriod.of(null, START));
    }

    @Test
    void 종료일이_시작일보다_빠르면_거부한다() {
        assertInvalidPeriod(() -> TravelPeriod.of(START, START.minusDays(1)));
    }

    @Test
    void 기준일과_같은_날짜는_미래가_아니다() {
        TravelPeriod period = TravelPeriod.of(START, START);

        assertThatCode(() -> period.validateNotAfter(START)).doesNotThrowAnyException();
    }

    @Test
    void 시작일이_기준일보다_미래이면_거부한다() {
        TravelPeriod period = TravelPeriod.of(START, null);

        assertInvalidPeriod(() -> period.validateNotAfter(START.minusDays(1)));
    }

    @Test
    void 종료일이_기준일보다_미래이면_거부한다() {
        TravelPeriod period = TravelPeriod.of(START, START.plusDays(2));

        assertInvalidPeriod(() -> period.validateNotAfter(START.plusDays(1)));
    }

    private void assertInvalidPeriod(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode().code())
                .isEqualTo("INVALID_TRAVEL_DATE_RANGE");
    }
}
