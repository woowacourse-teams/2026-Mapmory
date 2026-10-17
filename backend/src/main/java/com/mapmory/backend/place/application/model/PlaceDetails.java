package com.mapmory.backend.place.application.model;

import java.util.List;

/**
 * 제공자가 돌려준 장소 상세.
 *
 * <p>지역은 주소 이름(시·도, 시·군·구)인 addressAreas로 판정한다. Google은 Places 좌표를 경계
 * 판정(point-in-polygon)에 쓰는 것을 금지하므로 좌표를 받지 않는다. Google은 이름 저장도 금지해 name은 null이다.
 */
public record PlaceDetails(
        String placeId, String name, String countryCode,
        String attribution, String attributionUrl, List<String> addressAreas
) {
    public PlaceDetails {
        addressAreas = addressAreas == null ? List.of() : List.copyOf(addressAreas);
    }
}
