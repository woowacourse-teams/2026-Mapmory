package com.mapmory.backend.travelrecord.dto;

import com.mapmory.backend.recordmedia.ExpiringUrl;
import com.mapmory.backend.tag.Tag;
import com.mapmory.backend.tag.dto.TagSummaryResponse;
import com.mapmory.backend.travelrecord.TravelRecord;
import java.time.LocalDate;
import java.util.List;

public record TravelRecordListItemResponse(
        Long id,
        String title,
        String regionName,
        LocalDate startDate,
        LocalDate endDate,
        String thumbnailUrl,
        Long thumbnailUrlExpiresIn,
        List<TagSummaryResponse> tags
) {
    public static TravelRecordListItemResponse from(
            TravelRecord travelRecord,
            List<Tag> tags,
            ExpiringUrl thumbnailUrl
    ) {
        return new TravelRecordListItemResponse(
                travelRecord.getId(),
                travelRecord.getTitle(),
                travelRecord.getRegion().getName(),
                travelRecord.getStartDate(),
                travelRecord.getEndDate(),
                thumbnailUrl == null ? null : thumbnailUrl.url(),
                thumbnailUrl == null ? null : thumbnailUrl.expiresIn(),
                tags.stream().map(TagSummaryResponse::from).toList()
        );
    }
}
