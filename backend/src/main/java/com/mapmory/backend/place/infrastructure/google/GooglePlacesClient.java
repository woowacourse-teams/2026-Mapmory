package com.mapmory.backend.place.infrastructure.google;

import com.mapmory.backend.common.exception.BusinessException;
import com.mapmory.backend.place.application.PlaceErrorCode;
import com.mapmory.backend.place.application.model.PlaceCandidate;
import com.mapmory.backend.place.application.model.PlaceDetails;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;

/**
 * Google Places API (New)의 Autocomplete와 Place Details를 호출한다.
 *
 * <p>검색은 사진을 찾기 위한 도구다. Google이 준 후보 이름은 응답에만 쓰고 저장하지 않는다. 상세 조회는 이름과
 * 좌표 없이 주소 구성요소(Essentials)만 받아 국가와 시·도·시·군·구를 추천한다.
 */
@Component
public class GooglePlacesClient {

    static final String ATTRIBUTION = "Google Maps";
    static final String ATTRIBUTION_URL = "https://www.google.com/maps";

    // Place Details Essentials: 국가와 시·도·시·군·구 판정용 주소 구성요소만 받는다. 좌표와 이름은 받지 않는다.
    // displayName(Pro)을 넣으면 세션을 닫는 상세 조회가 Enterprise + Atmosphere로 과금된다.
    private static final String FIELD_MASK = "id,addressComponents";
    private static final String LANGUAGE = "ko";

    private final RestClient restClient;
    private final String apiKey;
    private final GoogleRequestLimiter requestLimiter;
    private final GooglePlaceMapper mapper = new GooglePlaceMapper();

    public GooglePlacesClient(
            RestClient.Builder builder,
            @Value("${google.places.base-url:https://places.googleapis.com}") String baseUrl,
            @Value("${google.places.api-key:}") String apiKey,
            GoogleRequestLimiter requestLimiter
    ) {
        this.restClient = builder.baseUrl(baseUrl).build();
        this.apiKey = apiKey;
        this.requestLimiter = requestLimiter;
    }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    public List<PlaceCandidate> search(String query, String sessionToken) {
        requireConfigured();
        requestLimiter.reserveAutocomplete();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("input", query);
        body.put("languageCode", LANGUAGE);
        if (sessionToken != null) {
            body.put("sessionToken", sessionToken);
        }
        JsonNode response = call(false, () -> restClient.post()
                .uri("/v1/places:autocomplete")
                .header("X-Goog-Api-Key", apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(JsonNode.class));
        return mapper.candidates(response);
    }

    /** 같은 sessionToken으로 상세를 조회해 자동완성 세션을 닫는다. 이름은 클라이언트가 후보의 name을 쓴다. */
    public PlaceDetails findForSelection(String placeId, String sessionToken) {
        return details(placeId, sessionToken);
    }

    public PlaceDetails findById(String placeId) {
        return details(placeId, null);
    }

    private PlaceDetails details(String placeId, String sessionToken) {
        if (placeId == null || !placeId.matches("[A-Za-z0-9_-]{1,255}")) {
            throw new BusinessException(PlaceErrorCode.INVALID_PLACE_ID);
        }
        requireConfigured();
        requestLimiter.reserveDetails();
        JsonNode response = call(true, () -> restClient.get()
                .uri(uri -> {
                    uri.path("/v1/places/{placeId}").queryParam("languageCode", LANGUAGE);
                    if (sessionToken != null) {
                        uri.queryParam("sessionToken", sessionToken);
                    }
                    return uri.build(placeId);
                })
                .header("X-Goog-Api-Key", apiKey)
                .header("X-Goog-FieldMask", FIELD_MASK)
                .retrieve()
                .body(JsonNode.class));
        return mapper.details(response, placeId)
                .orElseThrow(() -> new BusinessException(PlaceErrorCode.PLACE_NOT_FOUND));
    }

    private static JsonNode call(boolean placeLookup, Supplier<JsonNode> request) {
        try {
            JsonNode body = request.get();
            if (body == null || body.isNull()) {
                throw new BusinessException(PlaceErrorCode.PLACE_PROVIDER_UNAVAILABLE);
            }
            return body;
        } catch (RestClientResponseException exception) {
            if (placeLookup && isUnknownPlace(exception)) {
                throw new BusinessException(PlaceErrorCode.PLACE_NOT_FOUND);
            }
            throw new BusinessException(PlaceErrorCode.PLACE_PROVIDER_UNAVAILABLE);
        } catch (RestClientException exception) {
            throw new BusinessException(PlaceErrorCode.PLACE_PROVIDER_UNAVAILABLE);
        }
    }

    /** 장소 ID가 없거나 잘못된 경우만 PLACE_NOT_FOUND다. 잘못된 키도 400으로 오므로 제공자 장애로 본다. */
    private static boolean isUnknownPlace(RestClientResponseException exception) {
        int status = exception.getStatusCode().value();
        if (status == 404) {
            return true;
        }
        return status == 400 && !exception.getResponseBodyAsString().contains("API_KEY");
    }

    private void requireConfigured() {
        if (!isConfigured()) {
            throw new BusinessException(PlaceErrorCode.PLACE_PROVIDER_UNAVAILABLE,
                    "GOOGLE_PLACES_API_KEY가 설정되지 않았습니다.");
        }
    }
}
