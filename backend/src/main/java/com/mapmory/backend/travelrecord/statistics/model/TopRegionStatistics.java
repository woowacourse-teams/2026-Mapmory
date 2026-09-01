package com.mapmory.backend.travelrecord.statistics.model;

import com.mapmory.backend.region.RegionType;

public record TopRegionStatistics(
        Long regionId,
        String code,
        RegionType regionType,
        String name,
        long recordCount
) {
}
