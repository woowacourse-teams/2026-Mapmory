package com.mapmory.backend.travelrecord.application;

import com.mapmory.backend.recordmedia.ExpiringUrl;
import com.mapmory.backend.travelrecord.domain.RecordMedia;

/**
 * 미디어와 그 조회용 URL의 조합.
 *
 * <p>URL을 Object Key 기준 맵으로 넘기면 정렬 순서를 잃는다. 짝지은 목록으로 넘겨
 * 애그리거트가 정한 sortOrder 순서가 응답까지 그대로 이어지게 한다.
 *
 * @param viewUrl      원본. 전체 화면 뷰어용이다
 * @param thumbnailUrl 썸네일. 그리드처럼 작게 보여 줄 때 쓴다. 썸네일이 아직 없으면 원본이다
 */
public record MediaView(
        RecordMedia recordMedia,
        ExpiringUrl viewUrl,
        ExpiringUrl thumbnailUrl
) {
}
