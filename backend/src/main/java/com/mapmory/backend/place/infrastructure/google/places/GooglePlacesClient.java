package com.mapmory.backend.place.infrastructure.google.places;

import com.mapmory.backend.common.exception.BusinessException;
import com.mapmory.backend.place.application.port.PlaceLookupPort;
import com.mapmory.backend.place.domain.PlaceCandidate;
import com.mapmory.backend.place.domain.PlaceDetails;
import com.mapmory.backend.place.domain.PlaceId;
import com.mapmory.backend.place.domain.PlaceProvider;
import com.mapmory.backend.place.domain.SearchQuery;
import com.mapmory.backend.place.infrastructure.google.GoogleApiProperties;
import com.mapmory.backend.place.infrastructure.google.GoogleProvider;
import com.mapmory.backend.place.infrastructure.google.guard.GoogleApi;
import com.mapmory.backend.place.infrastructure.google.guard.GoogleCallGuard;
import com.mapmory.backend.place.exception.PlaceErrorCode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

/**
 * Google Places API (New)의 Autocomplete와 Place Details를 호출한다.
 *
 * <p>검색은 사진을 찾기 위한 도구다. Google이 준 후보 이름은 응답에만 쓰고 저장하지 않는다. 상세 조회는 이름과
 * 좌표 없이 주소 구성요소(Essentials)만 받아 국가와 시·도·시·군·구를 추천한다.
 *
 * <p>서킷 브레이커·월 한도·실패 처리는 {@link GoogleCallGuard}가 맡는다. 여기서는 요청을 만들고 응답을 읽기만 한다.
 */
@Component
public class GooglePlacesClient implements PlaceLookupPort {

    // Place Details Essentials: 국가와 시·도·시·군·구 판정용 주소 구성요소만 받는다. 좌표와 이름은 받지 않는다.
    // displayName(Pro)을 넣으면 세션을 닫는 상세 조회가 Enterprise + Atmosphere로 과금된다.
    private static final String FIELD_MASK = "id,addressComponents,shortFormattedAddress";
    private static final String LANGUAGE = "ko";

    private final RestClient restClient;
    private final GoogleApiProperties api;
    private final GoogleCallGuard google;
    private final GooglePlacesMapper mapper = new GooglePlacesMapper();

    public GooglePlacesClient(
            @Qualifier(GooglePlacesClientConfig.REST_CLIENT) RestClient restClient,
            GoogleApiProperties api,
            GoogleCallGuard google
    ) {
        this.restClient = restClient;
        this.api = api;
        this.google = google;
    }

    /** 키를 뺀 뒤에는 새 Google 장소를 받지 않도록 제공자 장애로 알린다. */
    @Override
    public PlaceProvider provider() {
        api.requireConfigured();
        return GoogleProvider.PLACE_PROVIDER;
    }

    @Override
    public List<PlaceCandidate> search(SearchQuery query, String sessionToken) {
        api.requireConfigured();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("input", query.value());
        body.put("languageCode", LANGUAGE);
        if (sessionToken != null) {
            body.put("sessionToken", sessionToken);
        }
        JsonNode response = google.call(GoogleApi.AUTOCOMPLETE,
                () -> restClient.post()
                        .uri("/v1/places:autocomplete")
                        .header("X-Goog-Api-Key", api.apiKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(body)
                        .retrieve()
                        .body(JsonNode.class));
        return mapper.candidates(response);
    }

    /** 같은 sessionToken으로 상세를 조회하면 자동완성 세션이 닫힌다. 이름은 클라이언트가 후보의 name을 쓴다. */
    @Override
    public PlaceDetails findDetails(PlaceId placeId, String sessionToken) {
        api.requireConfigured();
        JsonNode response = google.call(GoogleApi.DETAILS,
                () -> restClient.get()
                        .uri(uri -> {
                            uri.path("/v1/places/{placeId}").queryParam("languageCode", LANGUAGE);
                            if (sessionToken != null) {
                                uri.queryParam("sessionToken", sessionToken);
                            }
                            return uri.build(placeId.value());
                        })
                        .header("X-Goog-Api-Key", api.apiKey())
                        .header("X-Goog-FieldMask", FIELD_MASK)
                        .retrieve()
                        .body(JsonNode.class));
        return mapper.details(response, placeId)
                .orElseThrow(() -> new BusinessException(PlaceErrorCode.PLACE_NOT_FOUND));
    }
}
