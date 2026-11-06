package com.mapmory.backend.place.infrastructure.google;

import com.mapmory.backend.region.domain.RegionAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import tools.jackson.databind.JsonNode;

/**
 * Google 주소 구성요소(addressComponents)를 국가·시·도·시·군·구로 읽는다.
 * Places API (New)와 Geocoding v4가 같은 모양으로 주므로 두 API가 함께 쓴다.
 */
public final class GoogleAddressMapper {

    // 시·도 아래 단위를 큰 것부터. 예: 경기도 > 성남시 > 분당구, 서울특별시 > 종로구
    private static final List<String> AREA_TYPES = List.of(
            "administrative_area_level_2", "locality", "sublocality_level_1");

    private GoogleAddressMapper() {
    }

    /** 짧은 주소(shortAddress)는 Places 상세에만 있다. 없으면 null을 넘긴다. */
    public static RegionAddress address(JsonNode components, String shortAddress) {
        String province = componentText(components, "administrative_area_level_1");
        String countryCode = countryCode(components);
        List<String> subAreas = subAreas(components, province);
        if (countryCode == null && shortAddress != null) {
            // 독도처럼 Google이 구성요소에서 상위 지역을 모두 빼는 장소가 있다(독도리: 구성요소 "독도리"뿐,
            // 짧은 주소 "울릉군 울릉읍 독도리"). 이때만 짧은 주소의 낱말을 큰 단위부터 덧붙인다.
            subAreas = withShortAddressWords(subAreas, shortAddress);
        }
        return new RegionAddress(countryCode, province, subAreas);
    }

    private static List<String> withShortAddressWords(List<String> subAreas, String shortAddress) {
        List<String> areas = new ArrayList<>();
        for (String word : shortAddress.split("\\s+")) {
            if (!word.isBlank() && !areas.contains(word)) {
                areas.add(word);
            }
        }
        for (String area : subAreas) {
            if (!areas.contains(area)) {
                areas.add(area);
            }
        }
        return areas;
    }

    // 세종처럼 시·도와 같은 이름이 locality로 다시 오면 뺀다.
    private static List<String> subAreas(JsonNode components, String province) {
        List<String> areas = new ArrayList<>();
        for (String type : AREA_TYPES) {
            String name = componentText(components, type);
            if (name != null && !name.equals(province) && !areas.contains(name)) {
                areas.add(name);
            }
        }
        return areas;
    }

    private static String componentText(JsonNode components, String wantedType) {
        for (JsonNode component : components) {
            for (JsonNode type : component.path("types")) {
                if (wantedType.equals(type.asString())) {
                    return text(component.path("longText"));
                }
            }
        }
        return null;
    }

    private static String countryCode(JsonNode components) {
        for (JsonNode component : components) {
            for (JsonNode type : component.path("types")) {
                if ("country".equals(type.asString())) {
                    String code = text(component.path("shortText"));
                    if (code == null) {
                        return null;
                    }
                    return code.toUpperCase(Locale.ROOT);
                }
            }
        }
        return null;
    }

    public static String text(JsonNode node) {
        if (node == null || !node.isString()) {
            return null;
        }
        String value = node.asString().strip();
        return value.isEmpty() ? null : value;
    }
}
