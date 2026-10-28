package com.mapmory.backend.group.web.dto;

import com.mapmory.backend.group.application.AlbumDetail;
import com.mapmory.backend.travelrecord.api.RecordView;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record AlbumEntryResponse(
        Long travelRecordId,
        AlbumMemberResponse owner,
        String title,
        String content,
        LocalDate startDate,
        LocalDate endDate,
        String placeName,
        List<AlbumPhotoResponse> photos,
        LocalDateTime addedAt
) {

    static AlbumEntryResponse from(AlbumDetail.Entry entry) {
        RecordView record = entry.record();
        return new AlbumEntryResponse(
                record.id(),
                AlbumMemberResponse.from(entry.owner()),
                record.title(),
                record.content(),
                record.startDate(),
                record.endDate(),
                record.placeName(),
                record.photos().stream().map(AlbumPhotoResponse::from).toList(),
                entry.addedAt()
        );
    }
}
