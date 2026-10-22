package com.mapmory.backend.travelrecord;

import com.mapmory.backend.recordmedia.ExpiringUrl;
import java.util.Map;

/**
 * 일지 목록 한 페이지와, 이번 응답을 위해 새로 서명한 썸네일 URL을 묶는다.
 */
public record TravelRecordSummaries(
        TravelRecordListPage listPage,
        Map<Long, ExpiringUrl> thumbnailUrlsByTravelRecordId
) {
    public ExpiringUrl thumbnailUrlOf(TravelRecordListPage.Entry entry) {
        return thumbnailUrlsByTravelRecordId.get(entry.id());
    }
}
