package com.mapmory.backend.travelrecord.web.dto;

import com.mapmory.backend.travelrecord.application.TravelRecordCommand;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public record TravelRecordRequest(
        @Pattern(regexp = "[A-Z]{2}", message = "국가 코드는 대문자 2자리여야 합니다.")
        String countryCode,
        @Pattern(regexp = "\\S{1,20}", message = "시도 코드는 공백일 수 없습니다.")
        String provinceCode,
        @Pattern(regexp = "\\S{1,20}", message = "시군구 코드는 공백일 수 없습니다.")
        String districtCode,
        @Size(max = 200, message = "제목은 200자 이하여야 합니다.")
        String title,
        String content,
        @NotNull
        LocalDate startDate,
        LocalDate endDate,
        List<String> objectKeys,
        List<Long> tagIds,
        @Size(max = 255, message = "장소 ID는 255자 이하여야 합니다.")
        String placeId,
        /*
         * 사진 Object Key별 촬영일(yyyy-MM-dd). 선택이다. 모르는 사진은 빼고 보낸다.
         * 형식이 틀리거나 범위를 벗어난 날짜도 저장을 실패시키지 않고 모르는 날짜로 본다. 그래서 문자열로 받는다.
         */
        @Size(max = 100, message = "촬영일은 사진 수(100장)보다 많을 수 없습니다.")
        Map<String, String> capturedDates
) {
    public TravelRecordCommand toCommand() {
        return new TravelRecordCommand(
                countryCode,
                provinceCode,
                districtCode,
                title,
                content,
                startDate,
                endDate,
                objectKeys,
                tagIds,
                placeId,
                capturedDates
        );
    }
}
