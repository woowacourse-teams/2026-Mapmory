package com.mapmory.backend.travelrecord.dto;

import com.mapmory.backend.travelrecord.TravelRecordCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

public record TravelRecordRequest(
        @NotBlank(message = "국가 코드는 필수입니다.")
        @Pattern(regexp = "[A-Z]{2}", message = "국가 코드는 대문자 2자리여야 합니다.")
        String countryCode,
        @Pattern(regexp = "\\S{1,20}", message = "시도 코드는 공백일 수 없습니다.")
        String provinceCode,
        @Pattern(regexp = "\\S{1,20}", message = "시군구 코드는 공백일 수 없습니다.")
        String districtCode,
        @NotBlank(message = "제목은 필수입니다.")
        @Size(max = 200, message = "제목은 200자 이하여야 합니다.")
        String title,
        String content,
        @NotNull
        LocalDate startDate,
        LocalDate endDate,
        List<String> objectKeys,
        List<Long> tagIds
) {
    public TravelRecordRequest(
            String countryCode,
            String provinceCode,
            String districtCode,
            String title,
            String content,
            LocalDate startDate,
            LocalDate endDate,
            List<String> objectKeys
    ) {
        this(countryCode, provinceCode, districtCode, title, content, startDate, endDate, objectKeys, List.of());
    }

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
                tagIds
        );
    }
}
