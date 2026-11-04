package com.mapmory.backend.place.web.dto;

import com.mapmory.backend.place.application.SelectedPlace;
import com.mapmory.backend.place.domain.PlaceDetails;
import com.mapmory.backend.region.domain.Region;
import com.mapmory.backend.region.web.dto.RegionDetailResponse;

public record PlaceSelectionResponse(
        String placeId,
        String name,
        String countryCode,
        RegionDetailResponse suggestedRegion,
        boolean manualRegionRequired,
        String attribution,
        String attributionUrl
) {
    public static PlaceSelectionResponse from(SelectedPlace selected) {
        PlaceDetails place = selected.place();
        Region region = selected.suggestedRegion();
        // 제공자가 국가를 주지 않아도(독도) 지역을 찾았으면 그 지역의 국가를 알려 준다. 앱은 국가 코드로 지역 목록을 찾는다.
        String countryCode = place.address().countryCode() != null || region == null
                ? place.address().countryCode()
                : region.country().getRegionCode();
        return new PlaceSelectionResponse(
                place.id().value(), place.name(), countryCode,
                region == null ? null : RegionDetailResponse.from(region),
                selected.manualRegionRequired(), place.provider().attribution(), place.provider().attributionUrl());
    }
}
