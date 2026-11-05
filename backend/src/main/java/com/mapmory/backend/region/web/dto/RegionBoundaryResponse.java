package com.mapmory.backend.region.web.dto;

import com.mapmory.backend.region.domain.RegionBoundary;
import java.util.List;

public record RegionBoundaryResponse(
        String regionCode,
        String version,
        List<List<List<Double>>> rings
) {

    public static RegionBoundaryResponse from(RegionBoundary boundary) {
        return new RegionBoundaryResponse(boundary.regionCode(), boundary.version(), boundary.rings());
    }
}
