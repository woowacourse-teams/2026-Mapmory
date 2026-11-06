package com.mapmory.backend.place.infrastructure.google.places;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Places API (New) 호스트 주소(google.places). 키와 타임아웃은 공통 설정(google.*)을 쓴다. 테스트는 가짜 서버 주소로 바꾼다. */
@ConfigurationProperties(prefix = "google.places")
record GooglePlacesProperties(@DefaultValue("https://places.googleapis.com") String baseUrl) {
}
