package com.mapmory.backend.travelrecord.dto;

import com.mapmory.backend.travelrecord.TravelRecord;
import com.mapmory.backend.tag.dto.TagSummaryResponse;
import java.time.LocalDate;
import java.util.List;

public record TravelRecordListItemResponse(
        Long id,
        String title,
        String regionName,
        LocalDate startDate,
        LocalDate endDate,
        String thumbnailUrl,
        List<TagSummaryResponse> tags
) {
    public static TravelRecordListItemResponse from(
            TravelRecord travelRecord,
            List<TagSummaryResponse> tags
    ) {
        return new TravelRecordListItemResponse(
                travelRecord.getId(),
                travelRecord.getTitle(),
                travelRecord.getRegion().getName(),
                travelRecord.getStartDate(),
                travelRecord.getEndDate(),
                null, // 다음 단계에서 첫 번째 미디어의 URL을 넣는다.
                tags
        );
    }
}
