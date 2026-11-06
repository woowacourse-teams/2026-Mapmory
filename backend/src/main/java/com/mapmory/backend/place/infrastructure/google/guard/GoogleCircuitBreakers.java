package com.mapmory.backend.place.infrastructure.google.guard;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Function;

/** 호스트별 서킷 브레이커다. 호스트가 늘면 {@link GoogleEndpoint.Host}에 한 줄 더하면 된다. */
final class GoogleCircuitBreakers {

    private final Map<GoogleEndpoint.Host, CircuitBreaker> byHost;

    private GoogleCircuitBreakers(Map<GoogleEndpoint.Host, CircuitBreaker> byHost) {
        this.byHost = byHost;
    }

    static GoogleCircuitBreakers of(Function<GoogleEndpoint.Host, CircuitBreaker> factory) {
        Map<GoogleEndpoint.Host, CircuitBreaker> byHost = new EnumMap<>(GoogleEndpoint.Host.class);
        for (GoogleEndpoint.Host host : GoogleEndpoint.Host.values()) {
            byHost.put(host, factory.apply(host));
        }
        return new GoogleCircuitBreakers(byHost);
    }

    CircuitBreaker forEndpoint(GoogleEndpoint endpoint) {
        return byHost.get(endpoint.host);
    }
}
