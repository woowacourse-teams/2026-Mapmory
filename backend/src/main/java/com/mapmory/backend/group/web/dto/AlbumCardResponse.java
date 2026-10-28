package com.mapmory.backend.group.web.dto;

import com.mapmory.backend.group.application.AlbumPage;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record AlbumCardResponse(
        Long id,
        String name,
        RegionResponse region,
        LocalDate startDate,
        LocalDate endDate,
        PhotoUrlResponse coverPhoto,
        List<AlbumMemberResponse> contributors,
        int recordCount,
        int photoCount,
        LocalDateTime lastAddedAt
) {

    static AlbumCardResponse from(AlbumPage.Card card) {
        return new AlbumCardResponse(
                card.id(),
                card.name(),
                RegionResponse.from(card.region()),
                card.startDate(),
                card.endDate(),
                PhotoUrlResponse.fromNullable(card.cover()),
                card.contributors().stream().map(AlbumMemberResponse::from).toList(),
                card.recordCount(),
                card.photoCount(),
                card.lastAddedAt()
        );
    }
}
