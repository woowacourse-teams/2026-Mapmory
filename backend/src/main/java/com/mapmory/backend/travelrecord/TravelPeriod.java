package com.mapmory.backend.travelrecord;

import com.mapmory.backend.common.exception.BusinessException;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.time.LocalDate;

/**
 * 여행 기간. 시작일은 필수이고 종료일은 선택이며, 종료일은 시작일보다 빠를 수 없다.
 *
 * <p>"미래일 수 없다"는 규칙은 기준일에 따라 달라지므로 생성 시점에 검증하지 않는다.
 * 저장된 기록을 다시 읽을 때 기준일이 지나 있어도 유효한 기간이기 때문이다.
 * 이 규칙이 필요한 호출자가 {@link #validateNotAfter(LocalDate)}로 기준일을 넘겨 검증한다.
 */
@Embeddable
record TravelPeriod(
        @Column(name = "start_date", nullable = false)
        LocalDate startDate,

        @Column(name = "end_date")
        LocalDate endDate
) {
    TravelPeriod {
        validateStartDatePresent(startDate);
        validateEndDateNotBeforeStartDate(startDate, endDate);
    }

    static TravelPeriod of(LocalDate startDate, LocalDate endDate) {
        return new TravelPeriod(startDate, endDate);
    }

    void validateNotAfter(LocalDate baseDate) {
        if (startDate.isAfter(baseDate) || (endDate != null && endDate.isAfter(baseDate))) {
            throwInvalidTravelDateRange();
        }
    }

    private static void validateStartDatePresent(LocalDate startDate) {
        if (startDate == null) {
            throwInvalidTravelDateRange();
        }
    }

    private static void validateEndDateNotBeforeStartDate(LocalDate startDate, LocalDate endDate) {
        if (endDate != null && endDate.isBefore(startDate)) {
            throwInvalidTravelDateRange();
        }
    }

    private static void throwInvalidTravelDateRange() {
        throw new BusinessException(TravelRecordErrorCode.INVALID_TRAVEL_DATE_RANGE);
    }
}
