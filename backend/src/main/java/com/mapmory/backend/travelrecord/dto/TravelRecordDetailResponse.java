package com.mapmory.backend.travelrecord.dto;

import com.mapmory.backend.travelrecord.RecordMedia;
import com.mapmory.backend.tag.dto.TagSummaryResponse;
import com.mapmory.backend.travelrecord.TravelRecord;
import com.mapmory.backend.travelrecord.TravelRecordDetail;
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
        List<TravelRecordMediaResponse> media,
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
        this(id, title, content, region, startDate, endDate, objectKeys, List.of(), List.of(), createdAt, updatedAt);
    }

    public static TravelRecordDetailResponse from(TravelRecordDetail detail) {
        TravelRecord travelRecord = detail.travelRecord();

        return new TravelRecordDetailResponse(
                travelRecord.getId(),
                travelRecord.getTitle(),
                travelRecord.getContent(),
                RegionDetailResponse.from(travelRecord.getRegion()),
                travelRecord.getStartDate(),
                travelRecord.getEndDate(),
                detail.recordMedia().stream()
                        .map(RecordMedia::getObjectKey)
                        .toList(),
                detail.mediaViews().stream()
                        .map(TravelRecordMediaResponse::from)
                        .toList(),
                detail.tags().stream().map(TagSummaryResponse::from).toList(),
                travelRecord.getCreatedAt(),
                travelRecord.getUpdatedAt()
        );
    }
}
