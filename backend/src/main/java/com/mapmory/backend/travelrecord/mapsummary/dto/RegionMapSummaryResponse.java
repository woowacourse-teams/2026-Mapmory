package com.mapmory.backend.travelrecord.mapsummary.dto;

import com.mapmory.backend.region.RegionType;
import com.mapmory.backend.travelrecord.mapsummary.RegionMapSummary;
import java.util.List;
import com.mapmory.backend.travelrecord.mapsummary.policy.MapColorLevel;

public record RegionMapSummaryResponse(
        Long regionId,
        String code,
        RegionType regionType,
        String name,
        long count,
        MapColorLevel level
) {

    public static List<RegionMapSummaryResponse> from(List<RegionMapSummary> summaries) {
        return summaries.stream()
                .map(RegionMapSummaryResponse::from)
                .toList();
    }

    public static RegionMapSummaryResponse from(RegionMapSummary summary) {
        return new RegionMapSummaryResponse(
                summary.regionId(),
                summary.regionCode(),
                summary.regionType(),
                summary.name(),
                summary.recordCount(),
                summary.level()
        );
    }
}
