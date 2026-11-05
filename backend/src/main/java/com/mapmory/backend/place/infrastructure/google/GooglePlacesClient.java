package com.mapmory.backend.place.infrastructure.google;

import com.mapmory.backend.common.exception.BusinessException;
import com.mapmory.backend.common.http.ExternalCallFailure;
import com.mapmory.backend.place.application.port.PlaceLookupPort;
import com.mapmory.backend.place.domain.PlaceCandidate;
import com.mapmory.backend.place.domain.PlaceDetails;
import com.mapmory.backend.place.domain.PlaceId;
import com.mapmory.backend.place.domain.PlaceProvider;
import com.mapmory.backend.place.domain.SearchQuery;
import com.mapmory.backend.place.exception.PlaceErrorCode;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
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

    private static final Logger log = LoggerFactory.getLogger(GooglePlacesClient.class);

    static final PlaceProvider PROVIDER = new PlaceProvider("GOOGLE", "Google Maps", "https://www.google.com/maps");

    // Place Details Essentials: 국가와 시·도·시·군·구 판정용 주소 구성요소만 받는다. 좌표와 이름은 받지 않는다.
    // displayName(Pro)을 넣으면 세션을 닫는 상세 조회가 Enterprise + Atmosphere로 과금된다.
    private static final String FIELD_MASK = "id,addressComponents,shortFormattedAddress";
    private static final String LANGUAGE = "ko";

    private final RestClient restClient;
    private final GoogleApiProperties api;
    private final GoogleRequestLimiter requestLimiter;
    private final CircuitBreaker circuitBreaker;
    private final GooglePlaceMapper mapper = new GooglePlaceMapper();

    public GooglePlacesClient(
            @Qualifier(GooglePlacesClientConfig.PLACES_REST_CLIENT) RestClient restClient,
            GoogleApiProperties api,
            GoogleRequestLimiter requestLimiter,
            @Qualifier("googlePlacesCircuitBreaker") CircuitBreaker circuitBreaker
    ) {
        this.restClient = restClient;
        this.api = api;
        this.requestLimiter = requestLimiter;
        this.circuitBreaker = circuitBreaker;
    }

    /** 키를 뺀 뒤에는 새 Google 장소를 받지 않도록 제공자 장애로 알린다. */
    @Override
    public PlaceProvider provider() {
        requireConfigured();
        return PROVIDER;
    }

    @Override
    public List<PlaceCandidate> search(SearchQuery query, String sessionToken) {
        requireConfigured();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("input", query.value());
        body.put("languageCode", LANGUAGE);
        if (sessionToken != null) {
            body.put("sessionToken", sessionToken);
        }
        JsonNode response = call(false, requestLimiter::reserveAutocomplete, requestLimiter::refundAutocomplete,
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
        requireConfigured();
        JsonNode response = call(true, requestLimiter::reserveDetails, requestLimiter::refundDetails,
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
            ExternalCallFailure failure = ExternalCallFailure.of(exception);
            failure.timeoutPhase().ifPresent(phase -> log.atWarn()
                    .addKeyValue("event", "GOOGLE_PLACES_TIMEOUT")
                    .addKeyValue("phase", phase)
                    .log("Google Places 호출이 타임아웃으로 끊겼다: {}", phase));
            if (notBilled(failure)) {
                refund.run();
            }
            if (placeLookup && isUnknownPlace(failure)) {
                throw new BusinessException(PlaceErrorCode.PLACE_NOT_FOUND);
            }
            if (failure.rejected()) {
                // 4xx는 서킷이 실패로 세지 않는다. 키 거절처럼 고칠 때까지 계속되는 실패라 여기서 ERROR로 알린다.
                log.atError()
                        .addKeyValue("event", "GOOGLE_PLACES_REJECTED")
                        .addKeyValue("status", failure.status())
                        .setCause(exception)
                        .log("Google Places가 요청을 거절했다: {}", failure.status());
            }
            throw new BusinessException(PlaceErrorCode.PLACE_PROVIDER_UNAVAILABLE);
        }
        if (body == null || body.isNull()) {
            throw new BusinessException(PlaceErrorCode.PLACE_PROVIDER_UNAVAILABLE);
        }
        return body;
    }

    /**
     * Google이 과금하지 않은 실패인지 본다. 5xx는 Google이 처리하지 못한 것이고, 나가지 않은 요청은 Google에 닿지 않았다.
     * 답을 기다리다 끊긴 요청은 Google이 처리했을 수 있어 과금된 것으로 본다.
     */
    static boolean notBilled(ExternalCallFailure failure) {
        return failure.serverError() || !failure.sent();
    }

    /** 장소 ID가 없거나 잘못된 경우만 PLACE_NOT_FOUND다. 잘못된 키도 400으로 오므로 제공자 장애로 본다. */
    private static boolean isUnknownPlace(ExternalCallFailure failure) {
        int status = failure.status();
        if (status == 404) {
            return true;
        }
        return status == 400 && !failure.responseBody().contains("API_KEY");
    }

    private void requireConfigured() {
        if (!api.configured()) {
            throw new BusinessException(PlaceErrorCode.PLACE_PROVIDER_UNAVAILABLE,
                    "GOOGLE_PLACES_API_KEY가 설정되지 않았습니다.");
        }
    }
}
