package com.mapmory.backend.place.web.dto;

import com.mapmory.backend.place.application.LocatedRegion;
import com.mapmory.backend.region.domain.Region;
import com.mapmory.backend.region.web.dto.RegionDetailResponse;

/** 장소 선택 응답과 같은 모양이다. 앱은 manualRegionRequired면 지역을 직접 고르게 한다. */
public record LocationRegionResponse(
        String countryCode,
        RegionDetailResponse suggestedRegion,
        boolean manualRegionRequired,
        String attribution,
        String attributionUrl
) {
    public static LocationRegionResponse from(LocatedRegion located) {
        Region region = located.suggestedRegion();
        return new LocationRegionResponse(
                located.countryCode(),
                region == null ? null : RegionDetailResponse.from(region),
                located.manualRegionRequired(),
                located.provider().attribution(),
                located.provider().attributionUrl());
    }
}
