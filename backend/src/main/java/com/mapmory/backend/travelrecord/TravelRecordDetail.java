package com.mapmory.backend.travelrecord;

import com.mapmory.backend.recordmedia.RecordMedia;
import com.mapmory.backend.recordmedia.RecordMediaView;
import com.mapmory.backend.tag.Tag;
import java.util.List;

/**
 * 여행 일지 상세를 이루는 도메인 조합 결과다.
 * 표현 형식을 정하지 않으므로 응답 DTO 변환은 웹 계층이 담당한다.
 */
public record TravelRecordDetail(
        TravelRecord travelRecord,
        List<RecordMediaView> mediaViews,
        List<Tag> tags
) {
    public List<RecordMedia> recordMedia() {
        return mediaViews.stream()
                .map(RecordMediaView::recordMedia)
                .toList();
    }
}
