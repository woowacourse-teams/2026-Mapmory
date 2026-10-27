package com.mapmory.backend.region.dto;

import com.mapmory.backend.region.Region;

public record RegionDetailResponse(
        RegionItemResponse country,
        RegionItemResponse province,
        RegionItemResponse district
) {
    public static RegionDetailResponse from(Region region) {
        return switch (region.getRegionType()) {
            case COUNTRY -> new RegionDetailResponse(
                    RegionItemResponse.from(region),
                    null,
                    null
            );
            case PROVINCE -> new RegionDetailResponse(
                    RegionItemResponse.from(region.getRoot()),
                    RegionItemResponse.from(region),
                    null
            );
            case DISTRICT -> new RegionDetailResponse(
                    RegionItemResponse.from(region.getRoot()),
                    RegionItemResponse.from(region.getParent()),
                    RegionItemResponse.from(region)
            );
        };
    }
}
