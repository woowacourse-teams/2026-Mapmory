package com.mapmory.backend.travelrecord.mapsummary.dto;

import com.mapmory.backend.region.domain.RegionType;
import com.mapmory.backend.travelrecord.mapsummary.RegionMapSummary;
import com.mapmory.backend.travelrecord.mapsummary.policy.MapColorLevel;
import java.util.List;

/**
 * 지도 화면 전체 요약의 지역 하나. {@link RegionMapSummaryResponse}에 부모 지역 ID를 더한 것이다.
 *
 * @param parentRegionId 바로 위 지역 ID. 국가는 null이다. 앱은 이 값으로 국가·시도·시군구를 나눈다
 */
public record MapSummaryRegionResponse(
        Long regionId,
        Long parentRegionId,
        String code,
        RegionType regionType,
        String name,
        long count,
        MapColorLevel level
) {

    public static List<MapSummaryRegionResponse> from(List<RegionMapSummary> summaries) {
        return summaries.stream()
                .map(MapSummaryRegionResponse::from)
                .toList();
    }

    public static MapSummaryRegionResponse from(RegionMapSummary summary) {
        return new MapSummaryRegionResponse(
                summary.regionId(),
                summary.parentRegionId(),
                summary.regionCode(),
                summary.regionType(),
                summary.name(),
                summary.recordCount(),
                summary.level()
        );
    }
}
