package com.mapmory.backend.place.infrastructure.google;

import com.mapmory.backend.place.domain.PlaceCandidate;
import com.mapmory.backend.place.domain.PlaceDetails;
import com.mapmory.backend.place.domain.PlaceId;
import com.mapmory.backend.region.domain.RegionAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import tools.jackson.databind.JsonNode;

class GooglePlaceMapper {

    // Places API (New)와 Geocoding API는 주소 구성요소의 이름 필드가 다르다.
    private static final Fields PLACES = new Fields("longText", "shortText");
    private static final Fields GEOCODING = new Fields("long_name", "short_name");

    // 시·도 아래 단위를 큰 것부터. 예: 경기도 > 성남시 > 분당구, 서울특별시 > 종로구
    private static final List<String> AREA_TYPES = List.of(
            "administrative_area_level_2", "locality", "sublocality_level_1");

    List<PlaceCandidate> candidates(JsonNode response) {
        List<PlaceCandidate> candidates = new ArrayList<>();
        for (JsonNode suggestion : response.path("suggestions")) {
            JsonNode prediction = suggestion.path("placePrediction");
            String placeId = text(prediction.path("placeId"));
            String name = text(prediction.path("structuredFormat").path("mainText").path("text"));
            if (name == null) {
                name = text(prediction.path("text").path("text"));
            }
            // 고를 수 없는 후보는 뺀다. 형식이 맞지 않는 ID는 선택 API가 거절한다.
            if (!PlaceId.isValid(placeId) || name == null) {
                continue;
            }
            String address = text(prediction.path("structuredFormat").path("secondaryText").path("text"));
            candidates.add(new PlaceCandidate(PlaceId.from(placeId), name, address, GooglePlacesClient.PROVIDER));
        }
        return List.copyOf(candidates);
    }

    Optional<PlaceDetails> details(JsonNode response, PlaceId placeId) {
        JsonNode components = response.path("addressComponents");
        if (!components.isArray()) {
            return Optional.empty();
        }
        // 이름과 좌표는 요청하지 않는다. 이름은 클라이언트가 후보의 name을 쓰고,
        // Places 좌표를 경계 판정에 쓰는 것은 약관(3.2.3(c)(iv))이 금지한다.
        return Optional.of(new PlaceDetails(
                placeId,
                null,
                address(components, PLACES, text(response.path("shortFormattedAddress"))),
                GooglePlacesClient.PROVIDER));
    }

    /**
     * Geocoding 응답(status가 OK)에서 주소를 꺼낸다. 결과는 좁은 것(번지)부터 오고, 플러스 코드처럼 국가가 없는 결과도
     * 섞여 있어 국가가 있는 첫 결과를 쓴다. 독도처럼 어느 결과에도 국가가 없으면 시·도나 시·군·구 이름이 있는 첫 결과를 쓴다.
     * 둘 다 없으면 빈 값이다.
     */
    Optional<RegionAddress> geocodedAddress(JsonNode response) {
        RegionAddress withoutCountry = null;
        for (JsonNode result : response.path("results")) {
            JsonNode components = result.path("address_components");
            if (!components.isArray()) {
                continue;
            }
            RegionAddress address = address(components, GEOCODING, null);
            if (address.countryCode() != null) {
                return Optional.of(address);
            }
            if (withoutCountry == null && (address.province() != null || address.hasAreaNames())) {
                withoutCountry = address;
            }
        }
        return Optional.ofNullable(withoutCountry);
    }

    private static RegionAddress address(JsonNode components, Fields fields, String shortAddress) {
        String province = componentText(components, "administrative_area_level_1", fields);
        String countryCode = countryCode(components, fields);
        List<String> subAreas = subAreas(components, province, fields);
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
    private static List<String> subAreas(JsonNode components, String province, Fields fields) {
        List<String> areas = new ArrayList<>();
        for (String type : AREA_TYPES) {
            String name = componentText(components, type, fields);
            if (name != null && !name.equals(province) && !areas.contains(name)) {
                areas.add(name);
            }
        }
        return areas;
    }

    private static String componentText(JsonNode components, String wantedType, Fields fields) {
        for (JsonNode component : components) {
            for (JsonNode type : component.path("types")) {
                if (wantedType.equals(type.asString())) {
                    return text(component.path(fields.longName()));
                }
            }
        }
        return null;
    }

    private static String countryCode(JsonNode components, Fields fields) {
        for (JsonNode component : components) {
            for (JsonNode type : component.path("types")) {
                if ("country".equals(type.asString())) {
                    String code = text(component.path(fields.shortName()));
                    if (code == null) {
                        return null;
                    }
                    return code.toUpperCase(Locale.ROOT);
                }
            }
        }
        return null;
    }

    private record Fields(String longName, String shortName) {
    }

    private static String text(JsonNode node) {
        if (node == null || !node.isString()) {
            return null;
        }
        String value = node.asString().strip();
        return value.isEmpty() ? null : value;
    }
}
