package com.mapmory.backend.place.infrastructure.google;

import org.apache.hc.core5.util.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Google 호출용 RestClient를 만든다. 공통 값(spring.http.clients)이 아니라 Google에서 잰 타임아웃을 건다.
 * 관측(http.client.requests)은 자동 구성된 Builder에 남아 있어 그대로 기록된다.
 */
@Configuration
@EnableConfigurationProperties({GoogleApiProperties.class, GoogleRateLimitProperties.class})
class GooglePlacesClientConfig {

    private static final Logger log = LoggerFactory.getLogger(GooglePlacesClientConfig.class);

    static final String PLACES_REST_CLIENT = "googlePlacesRestClient";
    static final String GEOCODING_REST_CLIENT = "googleGeocodingRestClient";

    @Bean(PLACES_REST_CLIENT)
    RestClient googlePlacesRestClient(RestClient.Builder builder, GoogleApiProperties api,
                                      GoogleRateLimitProperties limits) {
        if (!api.configured()) {
            log.atError()
                    .addKeyValue("event", "GOOGLE_PLACES_NOT_CONFIGURED")
                    .log("GOOGLE_PLACES_API_KEY가 없어 장소 검색·선택과 사진 좌표 → 지역이 실패한다");
        }
        return builder.clone()
                .requestFactory(requestFactory(api, limits))
                .baseUrl(api.baseUrl())
                .build();
    }

    @Bean(GEOCODING_REST_CLIENT)
    RestClient googleGeocodingRestClient(RestClient.Builder builder, GoogleApiProperties api,
                                         GoogleRateLimitProperties limits) {
        return builder.clone()
                .requestFactory(requestFactory(api, limits))
                .baseUrl(api.geocodingBaseUrl())
                .build();
    }

    /**
     * Apache HttpClient로 고정한다. 클래스패스에 따라 골라지게 두면 라이브러리 하나로 타임아웃의 뜻이 몰래 바뀐다.
     *
     * <p>connect는 TCP와 TLS 연결까지, read는 연결된 뒤 응답을 기다리는 한도다. 둘이 따로라 잰 값에서 바로 정한다.
     * 재시도는 끈다. Apache는 GET이 503이면 1초 뒤 다시 보내는데, 우리는 다시 보내지 않고 바로 실패로 답한다.
     * 연결 풀은 분당 한도만큼 둔다. 분당 한도가 한꺼번에 몰려도 동시에 부를 수 있는 Google 호출 수의 상한이라,
     * 풀에서 연결을 기다리는 요청이 생기지 않는다. 풀에서 기다리는 한도는 connect와 같게 둔다.
     */
    static ClientHttpRequestFactory requestFactory(GoogleApiProperties api, GoogleRateLimitProperties limits) {
        int maxConnections = limits.requestsPerMinute();
        return ClientHttpRequestFactoryBuilder.httpComponents()
                .withHttpClientCustomizer(client -> client.disableAutomaticRetries())
                .withConnectionManagerCustomizer(pool -> pool
                        .setMaxConnTotal(maxConnections)
                        .setMaxConnPerRoute(maxConnections))
                .withDefaultRequestConfigCustomizer(request -> request
                        .setConnectionRequestTimeout(Timeout.of(api.connectTimeout())))
                .build(HttpClientSettings.defaults().withTimeouts(api.connectTimeout(), api.readTimeout()));
    }
}
