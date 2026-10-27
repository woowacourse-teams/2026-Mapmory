package com.mapmory.backend.place.infrastructure.google;

import com.mapmory.backend.place.domain.PlaceCandidate;
import com.mapmory.backend.place.domain.PlaceDetails;
import com.mapmory.backend.place.domain.PlaceId;
import com.mapmory.backend.region.RegionAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import tools.jackson.databind.JsonNode;

class GooglePlaceMapper {

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
                address(components),
                GooglePlacesClient.PROVIDER));
    }

    private static RegionAddress address(JsonNode components) {
        String province = componentText(components, "administrative_area_level_1");
        return new RegionAddress(countryCode(components), province, subAreas(components, province));
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

    private static String text(JsonNode node) {
        if (node == null || !node.isString()) {
            return null;
        }
        String value = node.asString().strip();
        return value.isEmpty() ? null : value;
    }
}
