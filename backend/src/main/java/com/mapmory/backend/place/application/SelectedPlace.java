package com.mapmory.backend.place.application;

import com.mapmory.backend.place.domain.PlaceDetails;
import com.mapmory.backend.region.Region;

/** 장소와 서버가 추천한 지역. 지역을 찾지 못하면 suggestedRegion은 null이다. */
public record SelectedPlace(PlaceDetails place, Region suggestedRegion) {

    /** 추천 지역이 없으면 사용자가 지역을 직접 고른다. */
    public boolean manualRegionRequired() {
        return suggestedRegion == null;
    }
}
