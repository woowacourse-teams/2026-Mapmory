package com.mapmory.backend.travelrecord;

import com.mapmory.backend.recordmedia.ExpiringUrl;
import com.mapmory.backend.tag.Tag;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;

/**
 * 여행 일지 목록 한 페이지와, 각 일지에 딸린 태그·썸네일을 묶은 도메인 조합 결과다.
 */
public record TravelRecordSummaries(
        Page<TravelRecord> travelRecords,
        Map<Long, List<Tag>> tagsByTravelRecordId,
        Map<Long, ExpiringUrl> thumbnailUrlsByTravelRecordId
) {
    public List<Tag> tagsOf(TravelRecord travelRecord) {
        return tagsByTravelRecordId.getOrDefault(travelRecord.getId(), List.of());
    }

    public ExpiringUrl thumbnailUrlOf(TravelRecord travelRecord) {
        return thumbnailUrlsByTravelRecordId.get(travelRecord.getId());
    }
}
