package com.mapmory.backend.place.infrastructure.google.geocoding;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Geocoding API 호스트 주소(google.geocoding). 키와 타임아웃은 공통 설정(google.*)을 쓴다. 테스트는 가짜 서버 주소로 바꾼다. */
@ConfigurationProperties(prefix = "google.geocoding")
record GoogleGeocodingProperties(@DefaultValue("https://geocode.googleapis.com") String baseUrl) {
}
