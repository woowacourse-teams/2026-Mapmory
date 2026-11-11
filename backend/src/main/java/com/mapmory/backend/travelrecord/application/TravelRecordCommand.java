package com.mapmory.backend.travelrecord.application;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 여행 일지 생성·수정에 필요한 값을 서비스 계층 형태로 담는다.
 * 웹 요청 스펙(TravelRecordRequest)과 분리해 서비스가 API 계약에 의존하지 않게 한다.
 *
 * <p>속성이 10개라 원시 값으로 풀면 순서만 바꿔도 컴파일이 통과하는 시그니처가 된다.
 * 그래서 Tag와 달리 커맨드를 둔다. (ADR 0017)
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
        List<Long> tagIds,
        String placeId,
        Map<String, String> capturedDates
) {
    public TravelRecordCommand {
        objectKeys = objectKeys == null ? List.of() : objectKeys;
        tagIds = tagIds == null ? List.of() : tagIds;
        capturedDates = capturedDates == null ? Map.of() : capturedDates;
    }

    /**
     * 사진 촬영일 없이 만든다. 촬영일을 받지 않는 v1 요청과 테스트가 쓴다.
     */
    public TravelRecordCommand(
            String countryCode,
            String provinceCode,
            String districtCode,
            String title,
            String content,
            LocalDate startDate,
            LocalDate endDate,
            List<String> objectKeys,
            List<Long> tagIds,
            String placeId
    ) {
        this(countryCode, provinceCode, districtCode, title, content, startDate, endDate, objectKeys, tagIds,
                placeId, Map.of());
    }

    public boolean hasRegion() {
        return countryCode != null || provinceCode != null || districtCode != null;
    }
}
