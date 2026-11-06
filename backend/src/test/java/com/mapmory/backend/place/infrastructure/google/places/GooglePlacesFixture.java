package com.mapmory.backend.place.infrastructure.google.places;

import com.mapmory.backend.place.infrastructure.google.GoogleCircuitBreakerProperties;
import com.mapmory.backend.place.infrastructure.google.GoogleCircuitBreakers;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import java.time.Clock;
import java.time.Duration;

/** 장소 검색 테스트가 실제 서킷 브레이커와 {@link GooglePlacesGuard}로 클라이언트를 만들 때 쓴다. */
final class GooglePlacesFixture {

    /** application.yaml의 google.places.circuit-breaker와 같은 값이다. */
    static final GoogleCircuitBreakerProperties BREAKER =
            new GoogleCircuitBreakerProperties(Duration.ofSeconds(60), 5, 50, Duration.ofSeconds(60), 3);

    private GooglePlacesFixture() {
    }

    static CircuitBreaker breaker(Clock clock) {
        return GoogleCircuitBreakers.create(BREAKER, clock, "googlePlaces", "GOOGLE_PLACES_CIRCUIT");
    }

    static GooglePlacesGuard guard(GooglePlacesLimiter limiter, Clock clock) {
        return new GooglePlacesGuard(breaker(clock), limiter);
    }
}
