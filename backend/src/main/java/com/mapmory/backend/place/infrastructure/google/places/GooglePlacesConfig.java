package com.mapmory.backend.place.infrastructure.google.places;

import com.mapmory.backend.place.exception.PlaceErrorCode;
import com.mapmory.backend.place.infrastructure.google.GoogleApiProperties;
import com.mapmory.backend.place.infrastructure.google.GoogleCircuitBreakers;
import com.mapmory.backend.place.infrastructure.google.GoogleHttpClients;
import com.mapmory.backend.place.infrastructure.google.GoogleMonthlyBudget;
import com.mapmory.backend.place.infrastructure.google.GoogleRequestPace;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * 장소 검색이 쓰는 RestClient(자기 연결 풀), 한도, 서킷 브레이커를 만든다. 사진 좌표 → 지역(geocoding)과 아무것도 나눠 쓰지 않아
 * 한쪽에 요청이 몰리거나 장애가 나도 다른 쪽을 막지 않는다. 같이 쓰는 건 키와 타임아웃(google.*)뿐이다.
 * 관측(http.client.requests)은 자동 구성된 Builder에 남아 있어 그대로 기록된다.
 */
@Configuration
@EnableConfigurationProperties({GoogleApiProperties.class, GooglePlacesProperties.class})
class GooglePlacesConfig {

    private static final Logger log = LoggerFactory.getLogger(GooglePlacesConfig.class);

    static final String REST_CLIENT = "googlePlacesRestClient";
    static final String REQUEST_FACTORY = "googlePlacesRequestFactory";
    static final String CIRCUIT_BREAKER = "googlePlacesCircuitBreaker";

    // 빈으로 두어야 컨텍스트가 내려갈 때 스프링이 연결 풀을 닫는다.
    @Bean(REQUEST_FACTORY)
    ClientHttpRequestFactory googlePlacesRequestFactory(GoogleApiProperties api, GooglePlacesProperties properties) {
        return GoogleHttpClients.requestFactory(api, properties.rateLimit().requestsPerMinute());
    }

    @Bean(REST_CLIENT)
    RestClient googlePlacesRestClient(RestClient.Builder builder, GoogleApiProperties api,
                                      GooglePlacesProperties properties,
                                      @Qualifier(REQUEST_FACTORY) ClientHttpRequestFactory requestFactory) {
        if (!api.configured()) {
            log.atError()
                    .addKeyValue("event", "GOOGLE_PLACES_NOT_CONFIGURED")
                    .log("GOOGLE_PLACES_API_KEY가 없어 장소 검색·선택과 사진 좌표 → 지역이 실패한다");
        }
        return builder.clone()
                .requestFactory(requestFactory)
                .baseUrl(properties.baseUrl())
                .build();
    }

    @Bean(CIRCUIT_BREAKER)
    CircuitBreaker googlePlacesCircuitBreaker(GooglePlacesProperties properties, Clock clock) {
        return GoogleCircuitBreakers.create(properties.circuitBreaker(), clock, "googlePlaces", "GOOGLE_PLACES_CIRCUIT");
    }

    @Bean
    GooglePlacesLimiter googlePlacesLimiter(ProxyManager<String> bucketManager, GooglePlacesProperties properties,
                                            Clock clock) {
        return limiter(bucketManager, properties.rateLimit(), clock);
    }

    static GooglePlacesLimiter limiter(ProxyManager<String> bucketManager, GooglePlacesProperties.RateLimit limit,
                                       Clock clock) {
        return new GooglePlacesLimiter(
                new GoogleRequestPace(limit.requestsPerMinute()),
                new GoogleMonthlyBudget(bucketManager, "autocomplete", limit.autocompletePerMonth(),
                        limit.freeAlertAt(), PlaceErrorCode.PLACE_SEARCH_BUDGET_EXHAUSTED, clock),
                new GoogleMonthlyBudget(bucketManager, "details", limit.detailsPerMonth(),
                        limit.freeAlertAt(), PlaceErrorCode.PLACE_PROVIDER_BUDGET_EXHAUSTED, clock));
    }
}
