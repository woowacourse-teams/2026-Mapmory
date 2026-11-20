package com.mapmory.backend.travelrecord.web.dto;

import com.mapmory.backend.travelrecord.application.MediaView;
import java.time.LocalDate;

/**
 * viewUrl은 원본(전체 화면 뷰어용), thumbnailUrl은 썸네일(그리드용)이다. 썸네일이 아직 없으면 thumbnailUrl도 원본이다.
 * capturedDate는 사진 촬영일이고 모르면 null이다.
 */
public record TravelRecordMediaResponse(
        Long id,
        String objectKey,
        String viewUrl,
        long viewUrlExpiresIn,
        String thumbnailUrl,
        long thumbnailUrlExpiresIn,
        int sortOrder,
        LocalDate capturedDate
) {
    public static TravelRecordMediaResponse from(MediaView mediaView) {
        return new TravelRecordMediaResponse(
                mediaView.recordMedia().getId(),
                mediaView.recordMedia().getObjectKey(),
                mediaView.viewUrl().url(),
                mediaView.viewUrl().expiresIn(),
                mediaView.thumbnailUrl().url(),
                mediaView.thumbnailUrl().expiresIn(),
                mediaView.recordMedia().getSortOrder(),
                mediaView.recordMedia().getCapturedDate()
        );
    }
}
