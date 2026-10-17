package com.mapmory.backend.place.infrastructure.google;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Google 장애를 판단하는 기준. 최근 window 동안 minimumCalls번 이상 불렀고 그중 failureRatePercent% 이상이
 * 실패면 openDuration 동안 Google을 부르지 않는다. 그 뒤 halfOpenCalls번 시험해 회복을 확인한다.
 */
@ConfigurationProperties(prefix = "google.places.circuit-breaker")
public record GoogleCircuitBreakerProperties(
        Duration window,
        int minimumCalls,
        int failureRatePercent,
        Duration openDuration,
        int halfOpenCalls
) {
    public GoogleCircuitBreakerProperties {
        if (window == null || window.toSeconds() < 1 || openDuration == null || openDuration.isNegative()
                || openDuration.isZero() || minimumCalls < 1 || halfOpenCalls < 1
                || failureRatePercent < 1 || failureRatePercent > 100) {
            throw new IllegalArgumentException("Google 서킷 브레이커 설정이 올바르지 않습니다.");
        }
    }
}
