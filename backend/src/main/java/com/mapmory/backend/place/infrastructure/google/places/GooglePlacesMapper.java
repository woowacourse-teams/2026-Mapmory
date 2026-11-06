package com.mapmory.backend.place.infrastructure.google.places;

import static com.mapmory.backend.place.infrastructure.google.GoogleAddressMapper.address;

import com.mapmory.backend.place.domain.PlaceCandidate;
import com.mapmory.backend.place.domain.PlaceDetails;
import com.mapmory.backend.place.domain.PlaceId;
import com.mapmory.backend.place.infrastructure.google.GoogleProvider;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import tools.jackson.databind.JsonNode;

class GooglePlacesMapper {

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
            candidates.add(new PlaceCandidate(PlaceId.from(placeId), name, address, GoogleProvider.PLACE_PROVIDER));
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
                address(components, text(response.path("shortFormattedAddress"))),
                GoogleProvider.PLACE_PROVIDER));
    }

    private static String text(JsonNode node) {
        if (node == null || !node.isString()) {
            return null;
        }
        String value = node.asString().strip();
        return value.isEmpty() ? null : value;
    }
}
