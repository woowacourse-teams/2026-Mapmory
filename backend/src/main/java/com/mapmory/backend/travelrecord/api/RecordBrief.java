package com.mapmory.backend.travelrecord.api;

import com.mapmory.backend.recordmedia.ExpiringUrl;
import java.time.LocalDate;

/**
 * 카드 한 장에 필요한 기록 요약.
 *
 * @param endDate 종료일이 없는 기록(당일 여행)은 시작일이다
 * @param cover   첫 사진의 썸네일. 썸네일이 없으면 원본을 서명한다. 사진이 없으면 null이다
 */
public record RecordBrief(
        Long id,
        Long memberId,
        String title,
        Long regionId,
        LocalDate startDate,
        LocalDate endDate,
        int photoCount,
        ExpiringUrl cover
) {
}
