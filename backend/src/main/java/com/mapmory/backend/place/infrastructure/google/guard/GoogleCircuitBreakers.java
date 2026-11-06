package com.mapmory.backend.place.infrastructure.google.guard;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Function;

/** 호스트별 서킷 브레이커다. 호스트가 늘면 {@link GoogleApi.Host}에 한 줄 더하면 된다. */
final class GoogleCircuitBreakers {

    private final Map<GoogleApi.Host, CircuitBreaker> byHost;

    private GoogleCircuitBreakers(Map<GoogleApi.Host, CircuitBreaker> byHost) {
        this.byHost = byHost;
    }

    static GoogleCircuitBreakers of(Function<GoogleApi.Host, CircuitBreaker> factory) {
        Map<GoogleApi.Host, CircuitBreaker> byHost = new EnumMap<>(GoogleApi.Host.class);
        for (GoogleApi.Host host : GoogleApi.Host.values()) {
            byHost.put(host, factory.apply(host));
        }
        return new GoogleCircuitBreakers(byHost);
    }

    CircuitBreaker forApi(GoogleApi api) {
        return byHost.get(api.host);
    }
}
