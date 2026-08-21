package com.mapmory.backend.travelrecord.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;

public record TravelRecordRequest(
        @NotNull
        String countryCode,
        String provinceCode,
        String districtCode,
        @NotNull
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
}
