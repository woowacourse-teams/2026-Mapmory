package com.mapmory.backend.place.application.model;

import com.mapmory.backend.region.Region;

/** 장소와 서버가 추천한 지역. 지역을 찾지 못하면 suggestedRegion은 null이다. */
public record SelectedPlace(PlaceDetails place, Region suggestedRegion) {
}
