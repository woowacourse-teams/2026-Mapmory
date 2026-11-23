package com.mapmory.backend.place.infrastructure.google.places;

import com.mapmory.backend.common.exception.BusinessException;
import com.mapmory.backend.common.http.ExternalCallFailure;
import com.mapmory.backend.place.exception.PlaceErrorCode;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;

/**
 * 장소 검색 Google 호출 하나의 순서: 서킷 브레이커 → 한도 확보 → 호출 → 실패면 과금 안 된 만큼 환불하고 우리 에러로 바꾼다.
 * 회로가 열려 있으면 한도를 확인하기 전에 막아, 부르지 않을 요청이 한도를 쓰지 않는다.
 *
 * <p>타임아웃은 어느 단계에서 끊겼는지 WARN, 4xx는 키 거절처럼 고칠 때까지 계속되는 실패라 ERROR다(서킷은 4xx를 세지 않는다).
 * 장소 요청에는 좌표가 없어 Google이 준 거절 사유를 로그에 남긴다.
 */
@Component
class GooglePlacesGuard {

    private static final Logger log = LoggerFactory.getLogger(GooglePlacesGuard.class);
    private static final int MAX_REASON_LENGTH = 500;

    private final CircuitBreaker circuitBreaker;
    private final GooglePlacesLimiter limiter;

    GooglePlacesGuard(@Qualifier(GooglePlacesConfig.CIRCUIT_BREAKER) CircuitBreaker circuitBreaker,
                      GooglePlacesLimiter limiter) {
        this.circuitBreaker = circuitBreaker;
        this.limiter = limiter;
    }

    JsonNode call(GooglePlacesEndpoint endpoint, Supplier<JsonNode> request) {
        JsonNode body;
        try {
            body = circuitBreaker.executeSupplier(() -> {
                limiter.reserve(endpoint);
                return request.get();
            });
        } catch (CallNotPermittedException exception) {
            throw unavailable();
        } catch (RestClientException exception) {
            ExternalCallFailure failure = ExternalCallFailure.of(exception);
            if (notBilled(failure)) {
                limiter.refund(endpoint);
            }
            throw toError(endpoint, failure);
        }
        if (body == null || body.isNull()) {
            throw unavailable();
        }
        return body;
    }

    /**
     * Google이 과금하지 않은 실패인지 본다. 5xx는 Google이 처리하지 못한 것이고, 나가지 않은 요청은 Google에 닿지 않았다.
     * 쿼터 초과(429)도 Google이 처리하지 않아 과금되지 않는다.
     * 답을 기다리다 끊긴 요청은 Google이 처리했을 수 있어 과금된 것으로 본다.
     */
    private static boolean notBilled(ExternalCallFailure failure) {
        return failure.serverError() || !failure.sent() || failure.status() == 429;
    }

    private static BusinessException toError(GooglePlacesEndpoint endpoint, ExternalCallFailure failure) {
        failure.timeoutPhase().ifPresent(phase -> log.atWarn()
                .addKeyValue("event", "GOOGLE_PLACES_TIMEOUT")
                .addKeyValue("phase", phase)
                .log("Google {} 호출이 타임아웃으로 끊겼다: {}", endpoint, phase));
        if (endpoint.notFound(failure)) {
            return new BusinessException(PlaceErrorCode.PLACE_NOT_FOUND);
        }
        if (failure.rejected()) {
            String reason = abbreviate(failure.responseBody());
            log.atError()
                    .addKeyValue("event", "GOOGLE_PLACES_REJECTED")
                    .addKeyValue("status", failure.status())
                    .addKeyValue("reason", reason)
                    .log("Google {} 요청이 거절됐다: {} {}", endpoint, failure.status(), reason);
        }
        return unavailable();
    }

    // 키 거절·API 미허용 같은 사유는 앞부분에 있다. 로그 한 줄이 너무 길어지지 않게 자른다.
    private static String abbreviate(String body) {
        return body.length() <= MAX_REASON_LENGTH ? body : body.substring(0, MAX_REASON_LENGTH) + "...";
    }

    private static BusinessException unavailable() {
        return new BusinessException(PlaceErrorCode.PLACE_PROVIDER_UNAVAILABLE);
    }
}
