package com.mapmory.backend.place.web.dto;

import com.mapmory.backend.place.application.SelectedPlace;
import com.mapmory.backend.place.domain.PlaceDetails;
import com.mapmory.backend.region.Region;
import com.mapmory.backend.region.dto.RegionDetailResponse;

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
        return new PlaceSelectionResponse(
                place.placeId(), place.name(), place.address().countryCode(),
                region == null ? null : RegionDetailResponse.from(region),
                region == null, place.attribution(), place.attributionUrl());
    }
}
