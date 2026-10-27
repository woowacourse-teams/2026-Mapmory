package com.mapmory.backend.travelrecord.web.dto;

import com.mapmory.backend.region.dto.RegionDetailResponse;
import com.mapmory.backend.tag.dto.TagSummaryResponse;
import com.mapmory.backend.travelrecord.application.TravelRecordDetail;
import com.mapmory.backend.travelrecord.domain.RecordMedia;
import com.mapmory.backend.travelrecord.domain.RecordedPlace;
import com.mapmory.backend.travelrecord.domain.TravelRecord;
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
        LocalDateTime updatedAt,
        String placeProvider,
        String placeId,
        String placeName,
        String placeAttribution,
        String placeAttributionUrl
) {
    public static TravelRecordDetailResponse from(TravelRecordDetail detail) {
        TravelRecord travelRecord = detail.travelRecord();
        RecordedPlace place = travelRecord.getPlace();

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
                travelRecord.getUpdatedAt(),
                place == null ? null : place.provider(),
                place == null ? null : place.id(),
                place == null ? null : place.name(),
                place == null ? null : place.attribution(),
                place == null ? null : place.attributionUrl()
        );
    }
}
