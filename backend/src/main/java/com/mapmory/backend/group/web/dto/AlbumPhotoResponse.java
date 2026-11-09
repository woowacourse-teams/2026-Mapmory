package com.mapmory.backend.group.web.dto;

import com.mapmory.backend.travelrecord.api.RecordView;

/**
 * TravelRecordMediaResponse에서 objectKey를 뺀 것. 촬영 시각은 저장하지 않아 아직 주지 않는다.
 *
 * <p>viewUrl은 원본(전체 화면 뷰어용), thumbnailUrl은 썸네일(그리드용)이다. 썸네일이 아직 없으면 thumbnailUrl도 원본이다.
 */
public record AlbumPhotoResponse(
        Long id,
        String viewUrl,
        long viewUrlExpiresIn,
        String thumbnailUrl,
        long thumbnailUrlExpiresIn,
        int sortOrder
) {

    static AlbumPhotoResponse from(RecordView.PhotoView photo) {
        return new AlbumPhotoResponse(
                photo.id(),
                photo.viewUrl().url(),
                photo.viewUrl().expiresIn(),
                photo.thumbnailUrl().url(),
                photo.thumbnailUrl().expiresIn(),
                photo.sortOrder()
        );
    }
}
