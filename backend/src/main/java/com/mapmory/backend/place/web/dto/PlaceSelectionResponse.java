package com.mapmory.backend.place.web.dto;

import com.mapmory.backend.place.application.model.PlaceDetails;
import com.mapmory.backend.place.application.model.SelectedPlace;
import com.mapmory.backend.region.Region;
import com.mapmory.backend.travelrecord.dto.RegionDetailResponse;

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
                place.placeId(), place.name(), place.countryCode(),
                region == null ? null : RegionDetailResponse.from(region),
                region == null, place.attribution(), place.attributionUrl());
    }
}
