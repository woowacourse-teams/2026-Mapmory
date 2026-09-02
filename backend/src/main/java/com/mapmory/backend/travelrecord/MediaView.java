package com.mapmory.backend.travelrecord;

import com.mapmory.backend.recordmedia.ExpiringUrl;

/**
 * 미디어와 그 조회용 URL의 조합.
 *
 * <p>URL을 Object Key 기준 맵으로 넘기면 정렬 순서를 잃는다. 짝지은 목록으로 넘겨
 * 애그리거트가 정한 sortOrder 순서가 응답까지 그대로 이어지게 한다.
 */
public record MediaView(
        RecordMedia recordMedia,
        ExpiringUrl viewUrl
) {
}
