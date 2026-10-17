package com.mapmory.backend.place.infrastructure.google;

import com.mapmory.backend.common.exception.BusinessException;
import com.mapmory.backend.place.application.PlaceErrorCode;
import com.mapmory.backend.place.application.model.PlaceCandidate;
import com.mapmory.backend.place.application.model.PlaceDetails;
import com.mapmory.backend.place.application.port.PlaceLookupPort;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import java.net.ConnectException;
import java.net.http.HttpConnectTimeoutException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;

/**
 * Google Places API (New)의 Autocomplete와 Place Details를 호출한다.
 *
 * <p>검색은 사진을 찾기 위한 도구다. Google이 준 후보 이름은 응답에만 쓰고 저장하지 않는다. 상세 조회는 이름과
 * 좌표 없이 주소 구성요소(Essentials)만 받아 국가와 시·도·시·군·구를 추천한다.
 *
 * <p>Google이 멈추면 서킷 브레이커가 열려 한도를 확인하기 전에 바로 실패한다. 실패 기준은 {@link GoogleCircuitBreakerConfig}에 있다.
 */
@Component
public class GooglePlacesClient implements PlaceLookupPort {

    static final String PROVIDER_CODE = "GOOGLE";
    static final String ATTRIBUTION = "Google Maps";
    static final String ATTRIBUTION_URL = "https://www.google.com/maps";

    // Place Details Essentials: 국가와 시·도·시·군·구 판정용 주소 구성요소만 받는다. 좌표와 이름은 받지 않는다.
    // displayName(Pro)을 넣으면 세션을 닫는 상세 조회가 Enterprise + Atmosphere로 과금된다.
    private static final String FIELD_MASK = "id,addressComponents";
    private static final String LANGUAGE = "ko";

    private final RestClient restClient;
    private final String apiKey;
    private final GoogleRequestLimiter requestLimiter;
    private final CircuitBreaker circuitBreaker;
    private final GooglePlaceMapper mapper = new GooglePlaceMapper();

    public GooglePlacesClient(
            RestClient.Builder builder,
            @Value("${google.places.base-url:https://places.googleapis.com}") String baseUrl,
            @Value("${google.places.api-key:}") String apiKey,
            GoogleRequestLimiter requestLimiter,
            CircuitBreaker circuitBreaker
    ) {
        this.restClient = builder.baseUrl(baseUrl).build();
        this.apiKey = apiKey;
        this.requestLimiter = requestLimiter;
        this.circuitBreaker = circuitBreaker;
    }

    @Override
    public String providerCode() {
        return PROVIDER_CODE;
    }

    @Override
    public List<PlaceCandidate> search(String query, String sessionToken) {
        requireConfigured();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("input", query);
        body.put("languageCode", LANGUAGE);
        if (sessionToken != null) {
            body.put("sessionToken", sessionToken);
        }
        JsonNode response = call(false, requestLimiter::reserveAutocomplete, requestLimiter::refundAutocomplete,
                () -> restClient.post()
                        .uri("/v1/places:autocomplete")
                        .header("X-Goog-Api-Key", apiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(body)
                        .retrieve()
                        .body(JsonNode.class));
        return mapper.candidates(response);
    }

    /** 같은 sessionToken으로 상세를 조회해 자동완성 세션을 닫는다. 이름은 클라이언트가 후보의 name을 쓴다. */
    @Override
    public PlaceDetails findForSelection(String placeId, String sessionToken) {
        return details(placeId, sessionToken);
    }

    @Override
    public PlaceDetails findById(String placeId) {
        return details(placeId, null);
    }

    /**
     * 기록에 붙일 장소를 Google을 다시 부르지 않고 만든다. 이름은 저장하지 않고 출처 표기는 상수라 다시 받을 값이 없다.
     *
     * <p>국가는 확인하지 않아 null이다. 앱을 고쳐 다른 나라 장소를 붙여도 그 회원의 기록에만 영향이 간다.
     * 키를 뺀 뒤에는 새 Google 장소를 받지 않도록 제공자 장애로 알린다.
     */
    @Override
    public PlaceDetails findForRecord(String placeId) {
        validatePlaceId(placeId);
        requireConfigured();
        return new PlaceDetails(placeId, null, null, ATTRIBUTION, ATTRIBUTION_URL, List.of());
    }

    private PlaceDetails details(String placeId, String sessionToken) {
        validatePlaceId(placeId);
        requireConfigured();
        JsonNode response = call(true, requestLimiter::reserveDetails, requestLimiter::refundDetails,
                () -> restClient.get()
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

    // 회로가 열려 있으면 한도를 확인하기 전에 막아, 부르지 않을 요청이 한도를 쓰지 않게 한다.
    private JsonNode call(boolean placeLookup, Runnable reserve, Runnable refund, Supplier<JsonNode> request) {
        JsonNode body;
        try {
            body = circuitBreaker.executeSupplier(() -> {
                reserve.run();
                return request.get();
            });
        } catch (CallNotPermittedException exception) {
            throw new BusinessException(PlaceErrorCode.PLACE_PROVIDER_UNAVAILABLE);
        } catch (RestClientException exception) {
            if (notBilled(exception)) {
                refund.run();
            }
            if (placeLookup && exception instanceof RestClientResponseException response && isUnknownPlace(response)) {
                throw new BusinessException(PlaceErrorCode.PLACE_NOT_FOUND);
            }
            throw new BusinessException(PlaceErrorCode.PLACE_PROVIDER_UNAVAILABLE);
        }
        if (body == null || body.isNull()) {
            throw new BusinessException(PlaceErrorCode.PLACE_PROVIDER_UNAVAILABLE);
        }
        return body;
    }

    /**
     * Google이 과금하지 않는 실패인지 본다. 5xx는 Google이 처리하지 못한 것이고, 연결하지 못한 요청은 Google에 닿지 않았다.
     * 연결한 뒤 답을 기다리다 끊긴 요청은 Google이 처리했을 수 있어 과금된 것으로 본다.
     */
    private static boolean notBilled(RestClientException exception) {
        if (exception instanceof HttpServerErrorException) {
            return true;
        }
        if (!(exception instanceof ResourceAccessException)) {
            return false;
        }
        for (Throwable cause = exception.getCause(); cause != null; cause = cause.getCause()) {
            if (cause instanceof ConnectException || cause instanceof HttpConnectTimeoutException) {
                return true;
            }
        }
        return false;
    }

    /** 장소 ID가 없거나 잘못된 경우만 PLACE_NOT_FOUND다. 잘못된 키도 400으로 오므로 제공자 장애로 본다. */
    private static boolean isUnknownPlace(RestClientResponseException exception) {
        int status = exception.getStatusCode().value();
        if (status == 404) {
            return true;
        }
        return status == 400 && !exception.getResponseBodyAsString().contains("API_KEY");
    }

    private static void validatePlaceId(String placeId) {
        if (placeId == null || !placeId.matches("[A-Za-z0-9_-]{1,255}")) {
            throw new BusinessException(PlaceErrorCode.INVALID_PLACE_ID);
        }
    }

    private void requireConfigured() {
        if (apiKey == null || apiKey.isBlank()) {
            throw new BusinessException(PlaceErrorCode.PLACE_PROVIDER_UNAVAILABLE,
                    "GOOGLE_PLACES_API_KEY가 설정되지 않았습니다.");
        }
    }
}
