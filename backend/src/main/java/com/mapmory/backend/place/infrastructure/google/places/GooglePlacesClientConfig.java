package com.mapmory.backend.place.infrastructure.google.places;

import com.mapmory.backend.place.infrastructure.google.GoogleConnectionConfig;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Places API (New) 호출용 RestClient. 연결(타임아웃·풀)은 {@link GoogleConnectionConfig}의 것을 같이 쓰고 주소만 다르다.
 * 관측(http.client.requests)은 자동 구성된 Builder에 남아 있어 그대로 기록된다.
 */
@Configuration
@EnableConfigurationProperties(GooglePlacesProperties.class)
class GooglePlacesClientConfig {

    static final String REST_CLIENT = "googlePlacesRestClient";

    @Bean(REST_CLIENT)
    RestClient googlePlacesRestClient(RestClient.Builder builder, GooglePlacesProperties properties,
            @Qualifier(GoogleConnectionConfig.REQUEST_FACTORY) ClientHttpRequestFactory requestFactory) {
        return builder.clone()
                .requestFactory(requestFactory)
                .baseUrl(properties.baseUrl())
                .build();
    }
}
