package com.mapmory.backend.place.infrastructure.google.geocoding;

import com.mapmory.backend.place.application.port.ReverseGeocodePort;
import com.mapmory.backend.place.domain.Coordinate;
import com.mapmory.backend.place.domain.PlaceProvider;
import com.mapmory.backend.place.infrastructure.google.GoogleApiProperties;
import com.mapmory.backend.place.infrastructure.google.GoogleProvider;
import com.mapmory.backend.place.infrastructure.google.guard.GoogleApi;
import com.mapmory.backend.place.infrastructure.google.guard.GoogleCallGuard;
import com.mapmory.backend.region.domain.RegionAddress;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

/**
 * Google Geocoding API v4로 사진 좌표를 주소 구성요소로 바꾼다. 좌표와 결과는 저장하지 않는다.
 *
 * <p>키는 URL이 아닌 헤더로 보낸다. 좌표는 v4가 GET 쿼리로만 받아 URL에 실리지만, HTTPS로 암호화되고 우리 로그에는
 * 남기지 않는다. 장소 검색과 같은 키·분당 한도·타임아웃을 쓰고, 월 한도와 서킷 브레이커는 따로 쓴다.
 *
 * <p>서킷 브레이커·월 한도·실패 처리는 {@link GoogleCallGuard}가 맡는다({@link GoogleApi#GEOCODE}).
 * 주소가 없는 좌표는 결과가 비어 오고 빈 값이다.
 */
@Component
public class GoogleGeocodingClient implements ReverseGeocodePort {

    // 국가·시·도·시·군·구 판정용 주소 구성요소만 받는다.
    private static final String FIELD_MASK = "results.addressComponents";
    private static final String LANGUAGE = "ko";

    private final RestClient restClient;
    private final GoogleApiProperties api;
    private final GoogleCallGuard google;
    private final GoogleGeocodingMapper mapper = new GoogleGeocodingMapper();

    public GoogleGeocodingClient(
            @Qualifier(GoogleGeocodingClientConfig.REST_CLIENT) RestClient restClient,
            GoogleApiProperties api,
            GoogleCallGuard google
    ) {
        this.restClient = restClient;
        this.api = api;
        this.google = google;
    }

    @Override
    public PlaceProvider provider() {
        return GoogleProvider.PLACE_PROVIDER;
    }

    @Override
    public Optional<RegionAddress> findAddress(Coordinate coordinate) {
        api.requireConfigured();
        JsonNode response = google.call(GoogleApi.GEOCODE, () -> restClient.get()
                .uri(uri -> uri.path("/v4/geocode/location")
                        .queryParam("location.latitude", coordinate.latitudeText())
                        .queryParam("location.longitude", coordinate.longitudeText())
                        .queryParam("languageCode", LANGUAGE)
                        .build())
                .header("X-Goog-Api-Key", api.apiKey())
                .header("X-Goog-FieldMask", FIELD_MASK)
                .retrieve()
                .body(JsonNode.class));
        return mapper.geocodedAddress(response);
    }
}
