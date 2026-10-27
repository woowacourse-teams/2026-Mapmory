package com.mapmory.backend.travelrecord.api;

import com.mapmory.backend.recordmedia.ExpiringUrl;
import java.time.LocalDate;
import java.util.List;

/**
 * 다른 사람에게 보여 줄 기록 하나. 태그와 장소 출처처럼 기록 주인에게만 의미 있는 값은 담지 않는다.
 *
 * @param endDate   종료일이 없는 기록(당일 여행)은 null이다
 * @param placeName 장소가 없거나 이름을 저장하지 않는 장소(Google)면 null이다
 * @param photos    sortOrder 순이다
 */
public record RecordView(
        Long id,
        Long memberId,
        String title,
        String content,
        LocalDate startDate,
        LocalDate endDate,
        String placeName,
        List<PhotoView> photos
) {

    public record PhotoView(Long id, ExpiringUrl viewUrl, int sortOrder) {
    }
}
