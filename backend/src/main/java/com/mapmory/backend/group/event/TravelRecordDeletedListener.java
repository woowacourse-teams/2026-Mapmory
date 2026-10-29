package com.mapmory.backend.group.event;

import com.mapmory.backend.group.application.AlbumEntryCleaner;
import com.mapmory.backend.travelrecord.api.TravelRecordDeleted;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 여행 기록이 지워지면 앨범에서 그 항목을 뗀다(ADR 0022 결정 5). 여행 기록은 그룹을 모르고 이벤트만 보낸다.
 *
 * <p>기록 삭제가 커밋된 뒤에 받는다. 여기서 실패해도 기록은 이미 지워졌으므로 예외를 올리지 않고 남긴다.
 * 떼지 못한 항목은 앨범을 읽을 때 걸러지므로 화면에는 나오지 않는다.
 */
@Component
class TravelRecordDeletedListener {

    private static final Logger log = LoggerFactory.getLogger(TravelRecordDeletedListener.class);

    private final AlbumEntryCleaner albumEntryCleaner;

    TravelRecordDeletedListener(AlbumEntryCleaner albumEntryCleaner) {
        this.albumEntryCleaner = albumEntryCleaner;
    }

    @TransactionalEventListener
    void onTravelRecordDeleted(TravelRecordDeleted event) {
        try {
            albumEntryCleaner.removeRecord(event.travelRecordId());
        } catch (RuntimeException exception) {
            log.atWarn()
                    .addKeyValue("event", "ALBUM_ENTRY_CLEANUP_FAILED")
                    .addKeyValue("travelRecordId", event.travelRecordId())
                    .setCause(exception)
                    .log("지워진 기록의 앨범 항목을 떼지 못했다");
        }
    }
}
