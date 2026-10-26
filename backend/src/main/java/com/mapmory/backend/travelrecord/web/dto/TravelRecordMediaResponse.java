package com.mapmory.backend.travelrecord.web.dto;

import com.mapmory.backend.recordmedia.ExpiringUrl;
import com.mapmory.backend.travelrecord.application.model.MediaView;
import com.mapmory.backend.travelrecord.application.model.RecordMedia;

public record TravelRecordMediaResponse(
        Long id,
        String objectKey,
        String viewUrl,
        long viewUrlExpiresIn,
        int sortOrder
) {
    public static TravelRecordMediaResponse from(MediaView mediaView) {
        return from(mediaView.recordMedia(), mediaView.viewUrl());
    }

    public static TravelRecordMediaResponse from(
            RecordMedia recordMedia,
            ExpiringUrl viewUrl
    ) {
        return new TravelRecordMediaResponse(
                recordMedia.getId(),
                recordMedia.getObjectKey(),
                viewUrl.url(),
                viewUrl.expiresIn(),
                recordMedia.getSortOrder()
        );
    }
}
