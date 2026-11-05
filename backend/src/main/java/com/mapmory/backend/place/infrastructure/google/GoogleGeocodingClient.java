package com.mapmory.backend.place.infrastructure.google;

import com.mapmory.backend.common.exception.BusinessException;
import com.mapmory.backend.place.application.port.ReverseGeocodePort;
import com.mapmory.backend.place.domain.Coordinate;
import com.mapmory.backend.place.domain.PlaceProvider;
import com.mapmory.backend.place.exception.PlaceErrorCode;
import com.mapmory.backend.region.domain.RegionAddress;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import java.time.Duration;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;

/**
 * Google Geocoding API v4로 사진 좌표를 주소 구성요소로 바꾼다. 좌표와 결과는 저장하지 않는다.
 *
 * <p>키는 URL이 아닌 헤더로 보낸다. 좌표는 v4가 GET 쿼리로만 받아 URL에 실리지만, HTTPS로 암호화되고 우리 로그에는
 * 남기지 않는다. 장소 검색과 같은 키·분당 한도·타임아웃을 쓰고, 월 한도와 서킷 브레이커는 따로 쓴다.
 *
 * <p>v4는 실패를 HTTP 상태로 알린다. Google은 성공한 요청만 과금하므로 4xx(키 거절 등)도 월 한도를 돌려주고 ERROR로
 * 알린다. 5xx·타임아웃은 서킷 브레이커가 실패로 센다. 주소가 없는 좌표는 결과가 비어 오고 빈 값이다.
 */
@Component
public class GoogleGeocodingClient implements ReverseGeocodePort {

    private static final Logger log = LoggerFactory.getLogger(GoogleGeocodingClient.class);

    static final String BASE_URL = "https://geocode.googleapis.com";
    // 국가·시·도·시·군·구 판정용 주소 구성요소만 받는다.
    private static final String FIELD_MASK = "results.addressComponents";
    private static final String LANGUAGE = "ko";

    private final RestClient restClient;
    private final String apiKey;
    private final GoogleRequestLimiter requestLimiter;
    private final CircuitBreaker circuitBreaker;
    private final GooglePlaceMapper mapper = new GooglePlaceMapper();

    @Autowired
    public GoogleGeocodingClient(
            RestClient.Builder builder,
            @Value("${google.geocoding.base-url:" + BASE_URL + "}") String baseUrl,
            @Value("${google.places.api-key:}") String apiKey,
            @Value("${google.places.connect-timeout}") Duration connectTimeout,
            @Value("${google.places.read-timeout}") Duration readTimeout,
            @Value("${google.places.rate-limit.requests-per-minute}") int maxConnections,
            GoogleRequestLimiter requestLimiter,
            @Qualifier("googleGeocodingCircuitBreaker") CircuitBreaker circuitBreaker
    ) {
        this(builder.requestFactory(GooglePlacesClient.requestFactory(connectTimeout, readTimeout, maxConnections)),
                baseUrl, apiKey, requestLimiter, circuitBreaker);
    }

    GoogleGeocodingClient(
            RestClient.Builder builder,
            String baseUrl,
            String apiKey,
            GoogleRequestLimiter requestLimiter,
            CircuitBreaker circuitBreaker
    ) {
        this.restClient = builder.baseUrl(baseUrl).build();
        this.apiKey = apiKey;
        this.requestLimiter = requestLimiter;
        this.circuitBreaker = circuitBreaker;
    }

    @Override
    public PlaceProvider provider() {
        return GooglePlacesClient.PROVIDER;
    }

    @Override
    public Optional<RegionAddress> findAddress(Coordinate coordinate) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new BusinessException(PlaceErrorCode.PLACE_PROVIDER_UNAVAILABLE,
                    "GOOGLE_PLACES_API_KEY가 설정되지 않았습니다.");
        }
        JsonNode response;
        try {
            // 회로가 열려 있으면 한도를 확인하기 전에 막아, 부르지 않을 요청이 한도를 쓰지 않게 한다.
            response = circuitBreaker.executeSupplier(() -> {
                requestLimiter.reserveGeocode();
                return restClient.get()
                        .uri(uri -> uri.path("/v4/geocode/location")
                                .queryParam("location.latitude", coordinate.latitudeText())
                                .queryParam("location.longitude", coordinate.longitudeText())
                                .queryParam("languageCode", LANGUAGE)
                                .build())
                        .header("X-Goog-Api-Key", apiKey)
                        .header("X-Goog-FieldMask", FIELD_MASK)
                        .retrieve()
                        .body(JsonNode.class);
            });
        } catch (CallNotPermittedException exception) {
            throw new BusinessException(PlaceErrorCode.PLACE_PROVIDER_UNAVAILABLE);
        } catch (RestClientException exception) {
            handleFailure(exception);
            throw new BusinessException(PlaceErrorCode.PLACE_PROVIDER_UNAVAILABLE);
        }
        if (response == null || response.isNull()) {
            throw new BusinessException(PlaceErrorCode.PLACE_PROVIDER_UNAVAILABLE);
        }
        return mapper.geocodedAddress(response);
    }

    // 예외 메시지에는 요청 URL(좌표)이 들어갈 수 있어 로그에 원인 예외를 붙이지 않는다.
    private void handleFailure(RestClientException exception) {
        String phase = GoogleFailures.timeoutPhase(exception);
        if (phase != null) {
            log.atWarn()
                    .addKeyValue("event", "GOOGLE_GEOCODING_TIMEOUT")
                    .addKeyValue("phase", phase)
                    .log("Google Geocoding 호출이 타임아웃으로 끊겼다: {}", phase);
        }
        boolean clientError = exception instanceof RestClientResponseException response
                && response.getStatusCode().is4xxClientError();
        if (clientError || GoogleFailures.notBilled(exception)) {
            requestLimiter.refundGeocode();
        }
        if (clientError) {
            int status = ((RestClientResponseException) exception).getStatusCode().value();
            // 4xx는 서킷이 실패로 세지 않는다. 키에 Geocoding API가 허용되지 않은 것처럼 고칠 때까지 계속되는 실패다.
            log.atError()
                    .addKeyValue("event", "GOOGLE_GEOCODING_REJECTED")
                    .addKeyValue("status", status)
                    .log("Google Geocoding이 요청을 거절했다: {}", status);
        }
    }
}
