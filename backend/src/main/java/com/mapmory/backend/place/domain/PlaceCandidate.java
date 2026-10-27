package com.mapmory.backend.place.domain;

import java.util.Objects;

/** 검색 후보. 사용자가 고를 수 있게 이름이 있어야 하고, 응답에만 쓰고 저장하지 않는다. address는 없을 수 있다. */
public record PlaceCandidate(PlaceId id, String name, String address, PlaceProvider provider) {

    public PlaceCandidate {
        Objects.requireNonNull(id, "장소 ID는 필수입니다.");
        Objects.requireNonNull(name, "장소 후보 이름은 필수입니다.");
        Objects.requireNonNull(provider, "장소 제공자는 필수입니다.");
    }
}
