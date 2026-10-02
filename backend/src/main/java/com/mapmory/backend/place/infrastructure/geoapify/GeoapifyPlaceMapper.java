package com.mapmory.backend.place.infrastructure.geoapify;

import com.mapmory.backend.place.application.model.PlaceCandidate;
import com.mapmory.backend.place.application.model.PlaceDetails;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import tools.jackson.databind.JsonNode;

/**
 * Geoapify 응답을 장소 포트의 제공자 중립 모델로 변환한다.
 */
final class GeoapifyPlaceMapper {

    List<PlaceCandidate> candidates(JsonNode response) {
        List<PlaceCandidate> candidates = new ArrayList<>();
        for (JsonNode result : response.path("results")) {
            String placeId = text(result, "place_id");
            String name = name(result);
            if (placeId != null && name != null) {
                candidates.add(new PlaceCandidate(placeId, name, text(result, "formatted"),
                        attribution(result), attributionUrl(result)));
            }
        }
        return List.copyOf(candidates);
    }

    Optional<PlaceDetails> details(JsonNode response, String placeId) {
        for (JsonNode feature : response.path("features")) {
            JsonNode properties = feature.path("properties");
            if (!"details".equals(text(properties, "feature_type"))) {
                continue;
            }
            String name = name(properties);
            String countryCode = text(properties, "country_code");
            JsonNode lat = properties.path("lat");
            JsonNode lon = properties.path("lon");
            if (name == null || !lat.isNumber() || !lon.isNumber()
                    || lat.asDouble() < -90 || lat.asDouble() > 90
                    || lon.asDouble() < -180 || lon.asDouble() > 180) {
                break;
            }
            return Optional.of(new PlaceDetails(placeId, name,
                    countryCode == null ? null : countryCode.toUpperCase(Locale.ROOT), lat.asDouble(), lon.asDouble(),
                    attribution(properties), attributionUrl(properties)));
        }
        return Optional.empty();
    }

    private String name(JsonNode node) {
        String name = text(node, "name");
        return name == null ? text(node, "address_line1") : name;
    }

    private String text(JsonNode node, String key) {
        JsonNode value = node.path(key);
        return value.isString() && !value.asString().isBlank() ? value.asString() : null;
    }

    private String attribution(JsonNode node) {
        String source = text(node.path("datasource"), "attribution");
        return source == null ? PlaceAttribution.OSM_TEXT : source;
    }

    private String attributionUrl(JsonNode node) {
        String source = text(node.path("datasource"), "url");
        return source == null ? PlaceAttribution.OSM_URL : source;
    }
}
