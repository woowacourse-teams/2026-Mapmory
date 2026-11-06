package com.mapmory.backend.place.infrastructure.google.geocoding;

import com.mapmory.backend.place.exception.PlaceErrorCode;
import com.mapmory.backend.place.infrastructure.google.GoogleApiProperties;
import com.mapmory.backend.place.infrastructure.google.GoogleCircuitBreakers;
import com.mapmory.backend.place.infrastructure.google.GoogleHttpClients;
import com.mapmory.backend.place.infrastructure.google.GoogleMonthlyBudget;
import com.mapmory.backend.place.infrastructure.google.GoogleRequestPace;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * 사진 좌표 → 지역이 쓰는 RestClient(자기 연결 풀), 한도, 서킷 브레이커를 만든다. 장소 검색(places)과 아무것도 나눠 쓰지 않는다.
 * 같이 쓰는 건 키와 타임아웃(google.*)뿐이다.
 */
@Configuration
@EnableConfigurationProperties({GoogleApiProperties.class, GoogleGeocodingProperties.class})
class GoogleGeocodingConfig {

    static final String REST_CLIENT = "googleGeocodingRestClient";
    static final String REQUEST_FACTORY = "googleGeocodingRequestFactory";
    static final String CIRCUIT_BREAKER = "googleGeocodingCircuitBreaker";

    // 빈으로 두어야 컨텍스트가 내려갈 때 스프링이 연결 풀을 닫는다.
    @Bean(REQUEST_FACTORY)
    ClientHttpRequestFactory googleGeocodingRequestFactory(GoogleApiProperties api, GoogleGeocodingProperties properties) {
        return GoogleHttpClients.requestFactory(api, properties.rateLimit().requestsPerMinute());
    }

    @Bean(REST_CLIENT)
    RestClient googleGeocodingRestClient(RestClient.Builder builder, GoogleApiProperties api,
                                         GoogleGeocodingProperties properties,
                                         @Qualifier(REQUEST_FACTORY) ClientHttpRequestFactory requestFactory) {
        return builder.clone()
                .requestFactory(requestFactory)
                .baseUrl(properties.baseUrl())
                .build();
    }

    @Bean(CIRCUIT_BREAKER)
    CircuitBreaker googleGeocodingCircuitBreaker(GoogleGeocodingProperties properties, Clock clock) {
        return GoogleCircuitBreakers.create(properties.circuitBreaker(), clock, "googleGeocoding",
                "GOOGLE_GEOCODING_CIRCUIT");
    }

    @Bean
    GoogleGeocodingLimiter googleGeocodingLimiter(ProxyManager<String> bucketManager,
                                                  GoogleGeocodingProperties properties, Clock clock) {
        return limiter(bucketManager, properties.rateLimit(), clock);
    }

    static GoogleGeocodingLimiter limiter(ProxyManager<String> bucketManager, GoogleGeocodingProperties.RateLimit limit,
                                          Clock clock) {
        return new GoogleGeocodingLimiter(
                new GoogleRequestPace(limit.requestsPerMinute()),
                new GoogleMonthlyBudget(bucketManager, "geocode", limit.perMonth(), limit.freeAlertAt(),
                        PlaceErrorCode.PLACE_PROVIDER_BUDGET_EXHAUSTED, clock));
    }
}
