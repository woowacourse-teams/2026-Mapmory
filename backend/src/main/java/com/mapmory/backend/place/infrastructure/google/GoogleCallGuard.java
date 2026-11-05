package com.mapmory.backend.place.infrastructure.google;

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
 * Google 호출 하나를 서킷 브레이커 → 한도 확보 → 호출 순서로 감싸고, 실패를 우리 에러로 바꾼다.
 * 클라이언트는 요청을 만들고 응답을 읽는 일만 한다.
 *
 * <ol>
 *   <li>회로가 열려 있으면 한도를 확인하기 전에 막는다. 부르지 않을 요청이 한도를 쓰지 않는다.</li>
 *   <li>과금되지 않은 실패({@link GoogleApi#notBilled})는 월 한도를 돌려준다.</li>
 *   <li>타임아웃은 어느 단계에서 끊겼는지 WARN, 4xx는 키 거절처럼 고칠 때까지 계속되는 실패라 ERROR로 남긴다.
 *       예외 메시지에 요청 URL(좌표)이 들어갈 수 있어 원인 예외는 붙이지 않는다.</li>
 * </ol>
 */
@Component
class GoogleCallGuard {

    private static final Logger log = LoggerFactory.getLogger(GoogleCallGuard.class);

    private final GoogleRequestLimiter requestLimiter;
    private final CircuitBreaker placesBreaker;
    private final CircuitBreaker geocodingBreaker;

    GoogleCallGuard(
            GoogleRequestLimiter requestLimiter,
            @Qualifier("googlePlacesCircuitBreaker") CircuitBreaker placesBreaker,
            @Qualifier("googleGeocodingCircuitBreaker") CircuitBreaker geocodingBreaker
    ) {
        this.requestLimiter = requestLimiter;
        this.placesBreaker = placesBreaker;
        this.geocodingBreaker = geocodingBreaker;
    }

    JsonNode call(GoogleApi api, Supplier<JsonNode> request) {
        JsonNode body;
        try {
            body = breaker(api).executeSupplier(() -> {
                api.reserve.accept(requestLimiter);
                return request.get();
            });
        } catch (CallNotPermittedException exception) {
            throw unavailable();
        } catch (RestClientException exception) {
            throw translate(api, ExternalCallFailure.of(exception));
        }
        if (body == null || body.isNull()) {
            throw unavailable();
        }
        return body;
    }

    private BusinessException translate(GoogleApi api, ExternalCallFailure failure) {
        failure.timeoutPhase().ifPresent(phase -> log.atWarn()
                .addKeyValue("event", api.host.eventPrefix + "_TIMEOUT")
                .addKeyValue("phase", phase)
                .log("Google {} 호출이 타임아웃으로 끊겼다: {}", api, phase));
        if (api.notBilled(failure)) {
            api.refund.accept(requestLimiter);
        }
        if (api.notFound(failure)) {
            return new BusinessException(PlaceErrorCode.PLACE_NOT_FOUND);
        }
        if (failure.rejected()) {
            // 4xx는 서킷이 실패로 세지 않아 여기서 알린다.
            log.atError()
                    .addKeyValue("event", api.host.eventPrefix + "_REJECTED")
                    .addKeyValue("status", failure.status())
                    .log("Google {} 요청이 거절됐다: {}", api, failure.status());
        }
        return unavailable();
    }

    private CircuitBreaker breaker(GoogleApi api) {
        return api.host == GoogleApi.Host.PLACES ? placesBreaker : geocodingBreaker;
    }

    private static BusinessException unavailable() {
        return new BusinessException(PlaceErrorCode.PLACE_PROVIDER_UNAVAILABLE);
    }
}
