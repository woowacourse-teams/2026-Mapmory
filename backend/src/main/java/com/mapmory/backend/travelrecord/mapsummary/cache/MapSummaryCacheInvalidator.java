package com.mapmory.backend.travelrecord.mapsummary.cache;

import com.mapmory.backend.tag.TagChangedEvent;
import com.mapmory.backend.travelrecord.TravelRecordChangedEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 커밋된 뒤에 지운다. 커밋 전에 지우면 그사이 다른 요청이 옛 데이터를 읽어 다시 채울 수 있다.
 */
@Component
class MapSummaryCacheInvalidator {

    private final MapSummaryCache mapSummaryCache;

    MapSummaryCacheInvalidator(MapSummaryCache mapSummaryCache) {
        this.mapSummaryCache = mapSummaryCache;
    }

    @TransactionalEventListener(fallbackExecution = true)
    void onTravelRecordChanged(TravelRecordChangedEvent event) {
        mapSummaryCache.evictMember(event.memberId());
    }

    @TransactionalEventListener(fallbackExecution = true)
    void onTagChanged(TagChangedEvent event) {
        mapSummaryCache.evictMember(event.memberId());
    }
}
