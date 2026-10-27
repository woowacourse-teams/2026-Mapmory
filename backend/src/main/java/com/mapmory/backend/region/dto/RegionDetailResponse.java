package com.mapmory.backend.region.dto;

import com.mapmory.backend.region.Region;

public record RegionDetailResponse(
        RegionItemResponse country,
        RegionItemResponse province,
        RegionItemResponse district
) {
    public static RegionDetailResponse from(Region region) {
        return new RegionDetailResponse(
                RegionItemResponse.from(region.country()),
                itemOrNull(region.province()),
                itemOrNull(region.district())
        );
    }

    private static RegionItemResponse itemOrNull(Region region) {
        return region == null ? null : RegionItemResponse.from(region);
    }
}
