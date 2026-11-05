package com.mapmory.backend.place.infrastructure.google;

import com.mapmory.backend.common.exception.BusinessException;
import com.mapmory.backend.place.application.port.PlaceLookupPort;
import com.mapmory.backend.place.application.port.ReverseGeocodePort;
import com.mapmory.backend.place.domain.Coordinate;
import com.mapmory.backend.place.domain.PlaceCandidate;
import com.mapmory.backend.place.domain.PlaceDetails;
import com.mapmory.backend.place.domain.PlaceId;
import com.mapmory.backend.place.domain.PlaceProvider;
import com.mapmory.backend.place.domain.SearchQuery;
import com.mapmory.backend.place.exception.PlaceErrorCode;
import com.mapmory.backend.region.domain.RegionAddress;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import org.apache.hc.client5.http.ConnectTimeoutException;
import org.apache.hc.core5.http.ConnectionRequestTimeoutException;
import org.apache.hc.core5.util.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestFactory;
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
 * <p>사진 좌표 → 지역은 Geocoding API의 역지오코딩으로 주소 구성요소만 받는다. 좌표와 결과는 저장하지 않는다.
 * 같은 키, 같은 연결 풀, 같은 서킷 브레이커와 분당 한도를 쓰고 월 한도만 따로 센다.
 *
 * <p>Google이 멈추면 서킷 브레이커가 열려 한도를 확인하기 전에 바로 실패한다. 실패 기준은 {@link GoogleCircuitBreakerConfig}에 있다.
 */
@Component
public class GooglePlacesClient implements PlaceLookupPort, ReverseGeocodePort {

    private static final Logger log = LoggerFactory.getLogger(GooglePlacesClient.class);

    static final PlaceProvider PROVIDER = new PlaceProvider("GOOGLE", "Google Maps", "https://www.google.com/maps");

    // Place Details Essentials: 국가와 시·도·시·군·구 판정용 주소 구성요소만 받는다. 좌표와 이름은 받지 않는다.
    // displayName(Pro)을 넣으면 세션을 닫는 상세 조회가 Enterprise + Atmosphere로 과금된다.
    private static final String FIELD_MASK = "id,addressComponents,shortFormattedAddress";
    private static final String LANGUAGE = "ko";
    static final String GEOCODING_BASE_URL = "https://maps.googleapis.com";

    private final RestClient restClient;
    private final RestClient geocodingClient;
    private final String apiKey;
    private final GoogleRequestLimiter requestLimiter;
    private final CircuitBreaker circuitBreaker;
    private final GooglePlaceMapper mapper = new GooglePlaceMapper();

    /**
     * Google 호출에만 따로 잰 타임아웃을 건다. 공통 값(spring.http.clients)은 재지 않은 JWKS·애플 호출이 계속 쓴다.
     * 관측(http.client.requests)은 자동 구성된 Builder에 남아 있어 그대로 기록된다.
     *
     * <p>연결 풀은 분당 한도(requests-per-minute)만큼 둔다. 분당 한도가 한꺼번에 몰려도 동시에 부를 수 있는
     * Google 호출 수의 상한이라, 풀에서 연결을 기다리는 요청이 생기지 않는다.
     */
    @Autowired
    public GooglePlacesClient(
            RestClient.Builder builder,
            @Value("${google.places.base-url:https://places.googleapis.com}") String baseUrl,
            @Value("${google.geocoding.base-url:" + GEOCODING_BASE_URL + "}") String geocodingBaseUrl,
            @Value("${google.places.api-key:}") String apiKey,
            @Value("${google.places.connect-timeout}") Duration connectTimeout,
            @Value("${google.places.read-timeout}") Duration readTimeout,
            @Value("${google.places.rate-limit.requests-per-minute}") int maxConnections,
            GoogleRequestLimiter requestLimiter,
            CircuitBreaker circuitBreaker
    ) {
        this(builder.requestFactory(requestFactory(connectTimeout, readTimeout, maxConnections)), baseUrl,
                geocodingBaseUrl, apiKey, requestLimiter, circuitBreaker);
    }

    GooglePlacesClient(
            RestClient.Builder builder,
            String baseUrl,
            String apiKey,
            GoogleRequestLimiter requestLimiter,
            CircuitBreaker circuitBreaker
    ) {
        this(builder, baseUrl, GEOCODING_BASE_URL, apiKey, requestLimiter, circuitBreaker);
    }

    GooglePlacesClient(
            RestClient.Builder builder,
            String baseUrl,
            String geocodingBaseUrl,
            String apiKey,
            GoogleRequestLimiter requestLimiter,
            CircuitBreaker circuitBreaker
    ) {
        // 같은 요청 팩토리(연결 풀, 타임아웃)를 나눠 쓰고 주소만 다르다.
        this.geocodingClient = builder.clone().baseUrl(geocodingBaseUrl).build();
        this.restClient = builder.baseUrl(baseUrl).build();
        this.apiKey = apiKey;
        this.requestLimiter = requestLimiter;
        this.circuitBreaker = circuitBreaker;
        if (apiKey == null || apiKey.isBlank()) {
            log.atError()
                    .addKeyValue("event", "GOOGLE_PLACES_NOT_CONFIGURED")
                    .log("GOOGLE_PLACES_API_KEY가 없어 장소 검색과 선택이 실패한다");
        }
    }

    /**
     * Apache HttpClient로 고정한다. 클래스패스에 따라 골라지게 두면 라이브러리 하나로 타임아웃의 뜻이 몰래 바뀐다.
     *
     * <p>connect는 TCP와 TLS 연결까지, read는 연결된 뒤 응답을 기다리는 한도다. 둘이 따로라 잰 값에서 바로 정한다.
     * 재시도는 끈다. Apache는 GET이 503이면 1초 뒤 다시 보내는데, 우리는 다시 보내지 않고 바로 실패로 답한다.
     * 풀에서 연결을 기다리는 한도는 connect와 같게 둔다. 풀이 동시 호출 상한만큼 커서 보통은 기다리지 않는다.
     */
    static ClientHttpRequestFactory requestFactory(Duration connectTimeout, Duration readTimeout, int maxConnections) {
        return ClientHttpRequestFactoryBuilder.httpComponents()
                .withHttpClientCustomizer(client -> client.disableAutomaticRetries())
                .withConnectionManagerCustomizer(pool -> pool
                        .setMaxConnTotal(maxConnections)
                        .setMaxConnPerRoute(maxConnections))
                .withDefaultRequestConfigCustomizer(request -> request
                        .setConnectionRequestTimeout(Timeout.of(connectTimeout)))
                .build(HttpClientSettings.defaults().withTimeouts(connectTimeout, readTimeout));
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
                        .header("X-Goog-Api-Key", apiKey)
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
                        .header("X-Goog-Api-Key", apiKey)
                        .header("X-Goog-FieldMask", FIELD_MASK)
                        .retrieve()
                        .body(JsonNode.class));
        return mapper.details(response, placeId)
                .orElseThrow(() -> new BusinessException(PlaceErrorCode.PLACE_NOT_FOUND));
    }

    /**
     * 좌표를 주소 구성요소로 바꾼다. Geocoding API는 실패도 HTTP 200에 status로 알린다. 주소가 없는 좌표(ZERO_RESULTS)만
     * 빈 값이고, 키 거절·쿼터 초과처럼 고칠 때까지 계속되는 실패는 ERROR로 알리고 제공자 장애로 답한다.
     * Google 쪽 일시 오류(UNKNOWN_ERROR)는 5xx와 같게 다뤄, 서킷 브레이커가 실패로 세고 월 한도를 돌려준다.
     * 좌표는 로그에 남기지 않는다.
     */
    @Override
    public Optional<RegionAddress> findAddress(Coordinate coordinate) {
        requireConfigured();
        JsonNode response = call(false, requestLimiter::reserveGeocode, requestLimiter::refundGeocode,
                () -> failOnGeocodingServerError(geocodingClient.get()
                        .uri(uri -> uri.path("/maps/api/geocode/json")
                                .queryParam("latlng", coordinate.toLatLng())
                                .queryParam("language", LANGUAGE)
                                .queryParam("key", apiKey)
                                .build())
                        .retrieve()
                        .body(JsonNode.class)));
        String status = response.path("status").asString("");
        return switch (status) {
            case "OK" -> mapper.geocodedAddress(response);
            case "ZERO_RESULTS" -> Optional.empty();
            default -> {
                // REQUEST_DENIED(키에 Geocoding API가 허용되지 않음), OVER_QUERY_LIMIT, INVALID_REQUEST.
                // Google은 이 응답을 사용량에 세지 않는다. 키를 고칠 때까지 월 한도가 새지 않도록 돌려준다.
                requestLimiter.refundGeocode();
                log.atError()
                        .addKeyValue("event", "GOOGLE_GEOCODING_REJECTED")
                        .addKeyValue("status", status)
                        .log("Google Geocoding이 요청을 거절했다: {}", status);
                throw new BusinessException(PlaceErrorCode.PLACE_PROVIDER_UNAVAILABLE);
            }
        };
    }

    private static JsonNode failOnGeocodingServerError(JsonNode body) {
        if (body != null && "UNKNOWN_ERROR".equals(body.path("status").asString(""))) {
            throw HttpServerErrorException.create(HttpStatus.SERVICE_UNAVAILABLE, "Geocoding UNKNOWN_ERROR",
                    null, null, null);
        }
        return body;
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
            logTimeout(exception);
            if (notBilled(exception)) {
                refund.run();
            }
            if (placeLookup && exception instanceof RestClientResponseException response && isUnknownPlace(response)) {
                throw new BusinessException(PlaceErrorCode.PLACE_NOT_FOUND);
            }
            if (exception instanceof RestClientResponseException response && response.getStatusCode().is4xxClientError()) {
                // 4xx는 서킷이 실패로 세지 않는다. 키 거절처럼 고칠 때까지 계속되는 실패라 여기서 ERROR로 알린다.
                log.atError()
                        .addKeyValue("event", "GOOGLE_PLACES_REJECTED")
                        .addKeyValue("status", response.getStatusCode().value())
                        .setCause(exception)
                        .log("Google Places가 요청을 거절했다: {}", response.getStatusCode().value());
            }
            throw new BusinessException(PlaceErrorCode.PLACE_PROVIDER_UNAVAILABLE);
        }
        if (body == null || body.isNull()) {
            throw new BusinessException(PlaceErrorCode.PLACE_PROVIDER_UNAVAILABLE);
        }
        return body;
    }

    /**
     * 타임아웃이 어느 단계였는지 남긴다. POOL이면 우리 풀에서 연결을 못 받은 것이고, CONNECT면 Google에 닿지 못한 것이고,
     * RESPONSE면 Google이 늦게 답한 것이다. Apache의 연결 타임아웃은 응답 타임아웃(SocketTimeoutException)을
     * 상속하므로 바깥 예외부터 보고 연결을 먼저 가린다.
     */
    private static void logTimeout(RestClientException exception) {
        if (!(exception instanceof ResourceAccessException)) {
            return;
        }
        for (Throwable cause = exception.getCause(); cause != null; cause = cause.getCause()) {
            String phase = timeoutPhase(cause);
            if (phase != null) {
                log.atWarn()
                        .addKeyValue("event", "GOOGLE_PLACES_TIMEOUT")
                        .addKeyValue("phase", phase)
                        .log("Google Places 호출이 타임아웃으로 끊겼다: {}", phase);
                return;
            }
        }
    }

    private static String timeoutPhase(Throwable cause) {
        if (cause instanceof ConnectionRequestTimeoutException) {
            return "POOL";
        }
        if (cause instanceof ConnectTimeoutException) {
            return "CONNECT";
        }
        if (cause instanceof SocketTimeoutException) {
            return "RESPONSE";
        }
        return null;
    }

    /**
     * Google이 과금하지 않는 실패인지 본다. 5xx는 Google이 처리하지 못한 것이고, 연결하지 못한 요청은 Google에 닿지 않았다.
     * 풀에서 연결을 못 받은 요청도 보내지 않았다. 연결한 뒤 답을 기다리다 끊긴 요청은 Google이 처리했을 수 있어
     * 과금된 것으로 본다.
     */
    private static boolean notBilled(RestClientException exception) {
        if (exception instanceof HttpServerErrorException) {
            return true;
        }
        if (!(exception instanceof ResourceAccessException)) {
            return false;
        }
        for (Throwable cause = exception.getCause(); cause != null; cause = cause.getCause()) {
            if (cause instanceof ConnectException || cause instanceof ConnectTimeoutException
                    || cause instanceof ConnectionRequestTimeoutException) {
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

    private void requireConfigured() {
        if (apiKey == null || apiKey.isBlank()) {
            throw new BusinessException(PlaceErrorCode.PLACE_PROVIDER_UNAVAILABLE,
                    "GOOGLE_PLACES_API_KEY가 설정되지 않았습니다.");
        }
    }
}
