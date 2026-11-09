package com.mapmory.backend.place.application;

import com.mapmory.backend.place.domain.PlaceDetails;
import com.mapmory.backend.region.domain.Region;

/** 장소와 서버가 추천한 지역. 지역을 찾지 못하면 suggestedRegion은 null이다. 규칙은 {@link RegionSuggestion}에 있다. */
public record SelectedPlace(PlaceDetails place, Region suggestedRegion) {

    public boolean manualRegionRequired() {
        return suggestion().manualRegionRequired();
    }

    public String countryCode() {
        return suggestion().countryCode();
    }

    private RegionSuggestion suggestion() {
        return new RegionSuggestion(place.address(), suggestedRegion);
    }
}
