package com.mapmory.backend.place.domain;

import java.util.Objects;

/** 장소를 내주는 제공자와 출처 표기. 제공자의 장소를 보여 주거나 저장할 때는 출처를 함께 둔다. */
public record PlaceProvider(String code, String attribution, String attributionUrl) {

    public PlaceProvider {
        Objects.requireNonNull(code, "장소 제공자 코드는 필수입니다.");
        Objects.requireNonNull(attribution, "장소 출처는 필수입니다.");
        Objects.requireNonNull(attributionUrl, "장소 출처 URL은 필수입니다.");
    }
}
