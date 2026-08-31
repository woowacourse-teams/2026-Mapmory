package com.mapmory.backend.recordmedia;

/**
 * 미디어 원본과 조회용 presigned URL을 함께 다루기 위한 조합이다.
 */
public record RecordMediaView(
        RecordMedia recordMedia,
        ExpiringUrl viewUrl
) {
}
