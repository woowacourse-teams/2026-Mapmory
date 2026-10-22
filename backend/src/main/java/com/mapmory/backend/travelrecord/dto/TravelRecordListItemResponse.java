package com.mapmory.backend.travelrecord.dto;

import com.mapmory.backend.recordmedia.ExpiringUrl;
import com.mapmory.backend.tag.dto.TagSummaryResponse;
import com.mapmory.backend.travelrecord.TravelRecordListPage;
import java.time.LocalDate;
import java.util.List;

public record TravelRecordListItemResponse(
        Long id,
        String title,
        String regionName,
        String placeName,
        String placeAttribution,
        String placeAttributionUrl,
        LocalDate startDate,
        LocalDate endDate,
        String thumbnailUrl,
        Long thumbnailUrlExpiresIn,
        int photoCount,
        List<TagSummaryResponse> tags
) {
    public static TravelRecordListItemResponse from(TravelRecordListPage.Entry entry, ExpiringUrl thumbnailUrl) {
        return new TravelRecordListItemResponse(
                entry.id(),
                entry.title(),
                entry.regionName(),
                entry.placeName(),
                entry.placeAttribution(),
                entry.placeAttributionUrl(),
                entry.startDate(),
                entry.endDate(),
                thumbnailUrl == null ? null : thumbnailUrl.url(),
                thumbnailUrl == null ? null : thumbnailUrl.expiresIn(),
                entry.photoCount(),
                entry.tags().stream()
                        .map(tag -> new TagSummaryResponse(tag.id(), tag.name()))
                        .toList()
        );
    }
}
