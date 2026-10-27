package com.mapmory.backend.group.application;

import com.mapmory.backend.recordmedia.ExpiringUrl;
import com.mapmory.backend.region.domain.Region;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 그룹 홈의 앨범 목록 한 페이지. 최근에 기록이 담긴 앨범이 먼저다. 페이지 모양은 기록 목록과 같다.
 */
public record AlbumPage(
        List<Card> items,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext
) {

    /**
     * 앨범 카드 하나. 기간·기록 수·사진 수는 지워지지 않은 기록들로 계산한다.
     *
     * @param cover        연 사람 기록의 첫 사진. 없으면 먼저 담긴 기록 중 사진이 있는 기록의 첫 사진, 그것도 없으면 null이다
     * @param contributors 기록을 담은 멤버들. 담은 순서다
     * @param lastAddedAt  마지막으로 기록이 담긴 시각. 목록 정렬 기준과 같다
     */
    public record Card(
            Long id,
            String name,
            Region region,
            LocalDate startDate,
            LocalDate endDate,
            ExpiringUrl cover,
            List<AlbumDetail.Contributor> contributors,
            int recordCount,
            int photoCount,
            LocalDateTime lastAddedAt
    ) {
    }
}
