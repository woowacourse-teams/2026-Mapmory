package com.mapmory.backend.region.dto;

import com.mapmory.backend.region.Region;

public record RegionItemResponse(
        String code,
        String name
) {
    public static RegionItemResponse from(Region region) {
        return new RegionItemResponse(region.getRegionCode(), region.getName());
    }
}
