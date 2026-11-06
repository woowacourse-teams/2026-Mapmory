package com.mapmory.backend.place.application;

import com.mapmory.backend.place.domain.PlaceDetails;
import com.mapmory.backend.region.domain.Region;

/** 장소와 서버가 추천한 지역. 지역을 찾지 못하면 suggestedRegion은 null이다. */
public record SelectedPlace(PlaceDetails place, Region suggestedRegion) {

    /** 추천 지역이 없으면 사용자가 지역을 직접 고른다. */
    public boolean manualRegionRequired() {
        return suggestedRegion == null;
    }

    /**
     * 장소의 국가 코드. 제공자가 국가를 주지 않아도(Google의 독도) 지역을 찾았으면 그 지역의 국가다.
     * 앱은 이 코드로 지역 목록을 찾아서, 비어 있으면 지역을 직접 고르게 한다.
     */
    public String countryCode() {
        String providerCountryCode = place.address().countryCode();
        if (providerCountryCode != null || suggestedRegion == null) {
            return providerCountryCode;
        }
        return suggestedRegion.country().getRegionCode();
    }
}
