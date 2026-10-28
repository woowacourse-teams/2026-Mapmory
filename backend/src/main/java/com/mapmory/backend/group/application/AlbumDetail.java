package com.mapmory.backend.group.application;

import com.mapmory.backend.region.domain.Region;
import com.mapmory.backend.travelrecord.api.RecordView;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 앨범 화면에 보여 줄 앨범과 담긴 기록들. 기록은 담은 순서다.
 *
 * <p>지워진 기록은 담지 않는다. 기간도 남은 기록들로 계산하고, 남은 기록이 없으면 null이다.
 *
 * @param myTravelRecordIds 내가 담은 기록들. 담은 순서이고, 지워진 기록은 빠진다. 담지 않았으면 비어 있다
 */
public record AlbumDetail(
        Long id,
        Long groupId,
        String name,
        Region region,
        LocalDate startDate,
        LocalDate endDate,
        Contributor opener,
        List<Long> myTravelRecordIds,
        List<Entry> entries
) {

    public record Contributor(Long memberId, String nickname, boolean me) {
    }

    public record Entry(RecordView record, Contributor owner, LocalDateTime addedAt) {
    }
}
