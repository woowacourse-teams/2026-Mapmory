package com.mapmory.backend.group.web.dto;

import com.mapmory.backend.travelrecord.api.RecordView;

/**
 * TravelRecordMediaResponse에서 objectKey를 뺀 것. 촬영 시각은 저장하지 않아 아직 주지 않는다.
 */
public record AlbumPhotoResponse(Long id, String viewUrl, long viewUrlExpiresIn, int sortOrder) {

    static AlbumPhotoResponse from(RecordView.PhotoView photo) {
        return new AlbumPhotoResponse(photo.id(), photo.viewUrl().url(), photo.viewUrl().expiresIn(), photo.sortOrder());
    }
}
