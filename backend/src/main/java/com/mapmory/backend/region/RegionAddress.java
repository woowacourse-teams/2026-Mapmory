package com.mapmory.backend.region;

import java.util.List;
import java.util.Objects;

/**
 * 지역을 추천할 때 쓰는 주소. 장소 제공자는 자기 응답을 이 모양으로 옮기고, region은 제공자를 모른다.
 *
 * @param countryCode ISO 3166-1 alpha-2 국가 코드. 모르면 null이다.
 * @param province    시·도 이름. 모르면 null이다.
 * @param subAreas    시·도 아래 행정구역 이름을 큰 단위부터. 예: 성남시, 분당구
 */
public record RegionAddress(String countryCode, String province, List<String> subAreas) {

    public static final RegionAddress UNKNOWN = new RegionAddress(null, null, List.of());

    public RegionAddress {
        province = blankToNull(province);
        subAreas = subAreas == null ? List.of() : subAreas.stream()
                .map(RegionAddress::blankToNull)
                .filter(Objects::nonNull)
                .toList();
    }

    private static String blankToNull(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        return name.strip();
    }
}
