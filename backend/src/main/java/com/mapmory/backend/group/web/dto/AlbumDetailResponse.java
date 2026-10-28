package com.mapmory.backend.group.web.dto;

import com.mapmory.backend.group.application.AlbumDetail;
import java.time.LocalDate;
import java.util.List;

/**
 * 앨범 상세. 글은 기록별로, 사진은 기록 안에 담는다. 앱은 사진을 풀어 한 그리드로 보여 준다.
 *
 * @param myTravelRecordIds 내가 담은 기록들. 담은 순서이고, 비어 있으면 아직 담지 않았다
 */
public record AlbumDetailResponse(
        Long id,
        Long groupId,
        String name,
        RegionResponse region,
        LocalDate startDate,
        LocalDate endDate,
        AlbumMemberResponse opener,
        List<Long> myTravelRecordIds,
        List<AlbumEntryResponse> entries
) {

    public static AlbumDetailResponse from(AlbumDetail detail) {
        return new AlbumDetailResponse(
                detail.id(),
                detail.groupId(),
                detail.name(),
                RegionResponse.from(detail.region()),
                detail.startDate(),
                detail.endDate(),
                AlbumMemberResponse.from(detail.opener()),
                detail.myTravelRecordIds(),
                detail.entries().stream().map(AlbumEntryResponse::from).toList()
        );
    }
}
