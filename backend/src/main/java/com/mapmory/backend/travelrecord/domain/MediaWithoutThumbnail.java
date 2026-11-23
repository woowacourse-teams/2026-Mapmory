package com.mapmory.backend.travelrecord.domain;

/**
 * 썸네일을 아직 만들지 않은 사진 한 장. 썸네일 워커가 읽는다.
 */
public record MediaWithoutThumbnail(Long id, String objectKey) {
}
