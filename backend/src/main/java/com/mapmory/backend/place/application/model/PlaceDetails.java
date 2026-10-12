package com.mapmory.backend.place.application.model;

import java.util.List;

/**
 * 제공자가 돌려준 장소 상세.
 *
 * <p>addressAreas가 있으면 지역을 주소 이름(시·도, 시·군·구)으로 판정한다. Google은 Places 좌표를
 * 경계 판정(point-in-polygon)에 쓰는 것을 금지하므로 좌표를 받지 않고 주소 구성요소만 넘긴다.
 * addressAreas가 null이면 좌표로 경계를 판정한다(Geoapify, OpenStreetMap 데이터).
 */
public record PlaceDetails(
        String placeId, String name, String countryCode, Double latitude, Double longitude,
        String attribution, String attributionUrl, List<String> addressAreas
) {
    public PlaceDetails {
        addressAreas = addressAreas == null ? null : List.copyOf(addressAreas);
    }

    public PlaceDetails(
            String placeId, String name, String countryCode, Double latitude, Double longitude,
            String attribution, String attributionUrl
    ) {
        this(placeId, name, countryCode, latitude, longitude, attribution, attributionUrl, null);
    }

    public boolean regionByAddress() {
        return addressAreas != null;
    }
}
