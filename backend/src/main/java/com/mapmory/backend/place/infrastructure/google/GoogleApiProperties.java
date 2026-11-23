package com.mapmory.backend.place.infrastructure.google;

import com.mapmory.backend.common.exception.BusinessException;
import com.mapmory.backend.place.exception.PlaceErrorCode;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Google 호출의 공통 설정(google.*). 장소 검색(Places API)과 사진 좌표 → 지역(Geocoding API)이 같은 키와 타임아웃을 쓴다.
 * 호스트 주소는 각 API 설정(google.places, google.geocoding)에 있다.
 * 키가 비어 있으면 앱은 뜨지만 두 기능 모두 PLACE_PROVIDER_UNAVAILABLE로 실패한다.
 */
@ConfigurationProperties(prefix = "google")
public record GoogleApiProperties(
        @DefaultValue("") String apiKey,
        Duration connectTimeout,
        Duration readTimeout
) {
    public boolean configured() {
        return apiKey != null && !apiKey.isBlank();
    }

    /** 키를 뺀 뒤에는 Google을 부르지 않도록 제공자 장애로 알린다. Places와 Geocoding 클라이언트가 함께 쓴다. */
    public void requireConfigured() {
        if (!configured()) {
            throw new BusinessException(PlaceErrorCode.PLACE_PROVIDER_UNAVAILABLE,
                    "GOOGLE_PLACES_API_KEY가 설정되지 않았습니다.");
        }
    }
}
