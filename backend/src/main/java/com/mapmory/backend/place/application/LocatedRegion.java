package com.mapmory.backend.place.application;

import com.mapmory.backend.place.domain.PlaceProvider;
import com.mapmory.backend.region.domain.Region;
import com.mapmory.backend.region.domain.RegionAddress;

/** 좌표로 찾은 지역. 지역을 찾지 못하면 suggestedRegion은 null이다. 규칙은 {@link RegionSuggestion}에 있다. */
public record LocatedRegion(RegionAddress address, Region suggestedRegion, PlaceProvider provider) {

    public boolean manualRegionRequired() {
        return suggestion().manualRegionRequired();
    }

    public String countryCode() {
        return suggestion().countryCode();
    }

    private RegionSuggestion suggestion() {
        return new RegionSuggestion(address, suggestedRegion);
    }
}
