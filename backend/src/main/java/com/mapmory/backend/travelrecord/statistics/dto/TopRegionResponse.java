package com.mapmory.backend.travelrecord.statistics.dto;

import com.mapmory.backend.region.RegionType;
import com.mapmory.backend.travelrecord.statistics.model.TopRegionStatistics;

public record TopRegionResponse(
        Long regionId,
        String code,
        RegionType regionType,
        String name,
        long recordCount
) {

    public static TopRegionResponse from(TopRegionStatistics statistics) {
        return new TopRegionResponse(
                statistics.regionId(),
                statistics.code(),
                statistics.regionType(),
                statistics.name(),
                statistics.recordCount()
        );
    }
}
