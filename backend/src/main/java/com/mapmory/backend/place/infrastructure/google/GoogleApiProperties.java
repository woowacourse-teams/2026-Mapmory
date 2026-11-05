package com.mapmory.backend.place.infrastructure.google;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Google 호출 설정. 장소 검색(Places API)과 사진 좌표 → 지역(Geocoding API)이 같은 키와 타임아웃을 쓴다.
 * 키가 비어 있으면 앱은 뜨지만 두 기능 모두 PLACE_PROVIDER_UNAVAILABLE로 실패한다.
 */
@ConfigurationProperties(prefix = "google.places")
public record GoogleApiProperties(
        @DefaultValue("") String apiKey,
        @DefaultValue("https://places.googleapis.com") String baseUrl,
        @DefaultValue("https://geocode.googleapis.com") String geocodingBaseUrl,
        Duration connectTimeout,
        Duration readTimeout
) {
    public boolean configured() {
        return apiKey != null && !apiKey.isBlank();
    }
}
