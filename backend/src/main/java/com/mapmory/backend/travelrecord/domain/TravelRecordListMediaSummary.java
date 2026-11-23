package com.mapmory.backend.travelrecord.domain;

/** 목록에 필요한 사진 개수와 첫 사진의 키만 조회한다. */
public interface TravelRecordListMediaSummary {

    Long getTravelRecordId();

    Long getPhotoCount();

    String getObjectKey();

    String getThumbKey();

    default String thumbnailObjectKey() {
        String thumbKey = getThumbKey();
        return thumbKey == null || thumbKey.isBlank() ? getObjectKey() : thumbKey;
    }
}
