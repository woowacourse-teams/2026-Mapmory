package com.mapmory.backend.travelrecord;

import com.mapmory.backend.tag.Tag;
import java.util.List;

/**
 * 여행 일지 상세를 이루는 도메인 조합 결과다.
 * 표현 형식을 정하지 않으므로 응답 DTO 변환은 웹 계층이 맡는다. (ADR 0016, 0017)
 */
public record TravelRecordDetail(
        TravelRecord travelRecord,
        List<Tag> tags,
        List<MediaView> mediaViews
) {
    public List<RecordMedia> recordMedia() {
        return mediaViews.stream()
                .map(MediaView::recordMedia)
                .toList();
    }
}
