package com.mapmory.backend.place.application;

import com.mapmory.backend.place.domain.PlaceProvider;
import com.mapmory.backend.region.domain.Region;
import com.mapmory.backend.region.domain.RegionAddress;

/** 좌표로 찾은 지역. 지역을 찾지 못하면 suggestedRegion은 null이다. */
public record LocatedRegion(RegionAddress address, Region suggestedRegion, PlaceProvider provider) {

    public boolean manualRegionRequired() {
        return suggestedRegion == null;
    }

    /** 제공자가 국가를 주지 않아도 지역을 찾았으면 그 지역의 국가다. 둘 다 없으면 null이다. */
    public String countryCode() {
        String providerCountryCode = address.countryCode();
        if (providerCountryCode != null || suggestedRegion == null) {
            return providerCountryCode;
        }
        return suggestedRegion.country().getRegionCode();
    }
}
