package com.mapmory.backend.travelrecord.dto;

import com.mapmory.backend.recordmedia.RecordMedia;
import com.mapmory.backend.tag.dto.TagSummaryResponse;
import com.mapmory.backend.travelrecord.TravelRecord;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record TravelRecordDetailResponse(
        Long id,
        String title,
        String content,
        RegionDetailResponse region,
        LocalDate startDate,
        LocalDate endDate,
        List<String> objectKeys,
        List<TagSummaryResponse> tags,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public TravelRecordDetailResponse(
            Long id,
            String title,
            String content,
            RegionDetailResponse region,
            LocalDate startDate,
            LocalDate endDate,
            List<String> objectKeys,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        this(id, title, content, region, startDate, endDate, objectKeys, List.of(), createdAt, updatedAt);
    }

    public static TravelRecordDetailResponse from(
            TravelRecord travelRecord,
            List<RecordMedia> recordMedia,
            List<TagSummaryResponse> tags
    ) {
        return new TravelRecordDetailResponse(
                travelRecord.getId(),
                travelRecord.getTitle(),
                travelRecord.getContent(),
                RegionDetailResponse.from(travelRecord.getRegion()),
                travelRecord.getStartDate(),
                travelRecord.getEndDate(),
                recordMedia.stream()
                        .map(RecordMedia::getObjectKey)
                        .toList(),
                tags,
                travelRecord.getCreatedAt(),
                travelRecord.getUpdatedAt()
        );
    }
}
