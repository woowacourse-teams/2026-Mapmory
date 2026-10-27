package com.mapmory.backend.place.domain;

import com.mapmory.backend.region.RegionAddress;
import java.util.Objects;

/**
 * 제공자가 돌려준 장소 상세.
 *
 * <p>지역은 좌표가 아니라 주소 이름(시·도, 시·군·구)인 address로 판정한다.
 * 제공자 약관상 이름을 저장할 수 없으면 name은 null이다.
 */
public record PlaceDetails(PlaceId id, String name, RegionAddress address, PlaceProvider provider) {

    public PlaceDetails {
        Objects.requireNonNull(id, "장소 ID는 필수입니다.");
        Objects.requireNonNull(provider, "장소 제공자는 필수입니다.");
        address = address == null ? RegionAddress.UNKNOWN : address;
    }

    /** 제공자를 다시 부르지 않고 ID와 출처만 가진 장소를 만든다. 이름과 주소는 모른다. */
    public static PlaceDetails withoutLookup(PlaceId id, PlaceProvider provider) {
        return new PlaceDetails(id, null, RegionAddress.UNKNOWN, provider);
    }
}
