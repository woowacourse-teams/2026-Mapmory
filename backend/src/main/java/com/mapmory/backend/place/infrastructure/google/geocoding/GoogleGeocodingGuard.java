package com.mapmory.backend.place.infrastructure.google.geocoding;

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
 * 역지오코딩 Google 호출 하나의 순서: 서킷 브레이커 → 한도 확보 → 호출 → 실패면 과금 안 된 만큼 환불하고 우리 에러로 바꾼다.
 * 회로가 열려 있으면 한도를 확인하기 전에 막아, 부르지 않을 요청이 한도를 쓰지 않는다.
 *
 * <p>타임아웃은 어느 단계에서 끊겼는지 WARN, 4xx는 키 거절처럼 고칠 때까지 계속되는 실패라 ERROR다(서킷은 4xx를 세지 않는다).
 * 예외 메시지와 응답 본문에 사진 좌표가 들어갈 수 있어 원인 예외와 거절 사유는 로그에 남기지 않는다.
 */
@Component
class GoogleGeocodingGuard {

    private static final Logger log = LoggerFactory.getLogger(GoogleGeocodingGuard.class);

    private final CircuitBreaker circuitBreaker;
    private final GoogleGeocodingLimiter limiter;

    GoogleGeocodingGuard(@Qualifier(GoogleGeocodingConfig.CIRCUIT_BREAKER) CircuitBreaker circuitBreaker,
                         GoogleGeocodingLimiter limiter) {
        this.circuitBreaker = circuitBreaker;
        this.limiter = limiter;
    }

    JsonNode call(Supplier<JsonNode> request) {
        JsonNode body;
        try {
            body = circuitBreaker.executeSupplier(() -> {
                limiter.reserve();
                return request.get();
            });
        } catch (CallNotPermittedException exception) {
            throw unavailable();
        } catch (RestClientException exception) {
            ExternalCallFailure failure = ExternalCallFailure.of(exception);
            if (notBilled(failure)) {
                limiter.refund();
            }
            throw toError(failure);
        }
        if (body == null || body.isNull()) {
            throw unavailable();
        }
        return body;
    }

    /**
     * Geocoding은 성공한 요청만 과금하므로 4xx도 돌려준다. 5xx, 나가지 않은 요청도 돌려준다.
     * 답을 기다리다 끊긴 요청은 Google이 처리했을 수 있어 과금된 것으로 본다.
     */
    private static boolean notBilled(ExternalCallFailure failure) {
        return failure.serverError() || !failure.sent() || failure.rejected();
    }

    private static BusinessException toError(ExternalCallFailure failure) {
        failure.timeoutPhase().ifPresent(phase -> log.atWarn()
                .addKeyValue("event", "GOOGLE_GEOCODING_TIMEOUT")
                .addKeyValue("phase", phase)
                .log("Google GEOCODE 호출이 타임아웃으로 끊겼다: {}", phase));
        if (failure.rejected()) {
            log.atError()
                    .addKeyValue("event", "GOOGLE_GEOCODING_REJECTED")
                    .addKeyValue("status", failure.status())
                    .addKeyValue("reason", "(숨김: 좌표 요청)")
                    .log("Google GEOCODE 요청이 거절됐다: {} (숨김: 좌표 요청)", failure.status());
        }
        return unavailable();
    }

    private static BusinessException unavailable() {
        return new BusinessException(PlaceErrorCode.PLACE_PROVIDER_UNAVAILABLE);
    }
}
