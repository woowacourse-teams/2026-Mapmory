package com.mapmory.backend.place.infrastructure.google;

import com.mapmory.backend.place.domain.PlaceProvider;

/** 장소 검색과 사진 좌표 → 지역이 같은 제공자로 기록된다. */
public final class GoogleProvider {

    public static final PlaceProvider PLACE_PROVIDER =
            new PlaceProvider("GOOGLE", "Google Maps", "https://www.google.com/maps");

    private GoogleProvider() {
    }
}
