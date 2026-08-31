package com.mapmory.backend.travelrecord;

import java.time.LocalDate;
import java.util.List;

/**
 * 여행 일지 생성·수정에 필요한 값을 서비스 계층 형태로 담는다.
 * 웹 요청 스펙(TravelRecordRequest)과 분리해 서비스가 API 계약에 의존하지 않게 한다.
 */
public record TravelRecordCommand(
        String countryCode,
        String provinceCode,
        String districtCode,
        String title,
        String content,
        LocalDate startDate,
        LocalDate endDate,
        List<String> objectKeys,
        List<Long> tagIds
) {
    public TravelRecordCommand {
        objectKeys = objectKeys == null ? List.of() : objectKeys;
        tagIds = tagIds == null ? List.of() : tagIds;
    }
}
