package com.mapmory.backend.place.infrastructure.google.guard;

import com.mapmory.backend.common.http.ExternalCallFailure;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;

/**
 * Google 호출 하나의 순서만 정한다: 서킷 브레이커 → 한도 확보 → 호출 → 실패면 과금 안 된 만큼 환불.
 * 회로가 열려 있으면 한도를 확인하기 전에 막아, 부르지 않을 요청이 한도를 쓰지 않는다.
 * 실패를 우리 에러로 바꾸는 일은 {@link GoogleFailureTranslator}가 한다.
 */
@Component
public class GoogleCallGuard {

    private final GoogleCircuitBreakers circuitBreakers;
    private final GoogleRequestLimiter requestLimiter;

    GoogleCallGuard(GoogleCircuitBreakers circuitBreakers, GoogleRequestLimiter requestLimiter) {
        this.circuitBreakers = circuitBreakers;
        this.requestLimiter = requestLimiter;
    }

    public JsonNode call(GoogleEndpoint endpoint, Supplier<JsonNode> request) {
        JsonNode body;
        try {
            body = circuitBreakers.forEndpoint(endpoint).executeSupplier(() -> {
                requestLimiter.reserve(endpoint);
                return request.get();
            });
        } catch (CallNotPermittedException exception) {
            throw GoogleFailureTranslator.unavailable();
        } catch (RestClientException exception) {
            ExternalCallFailure failure = ExternalCallFailure.of(exception);
            if (endpoint.notBilled(failure)) {
                requestLimiter.refund(endpoint);
            }
            throw GoogleFailureTranslator.toError(endpoint, failure);
        }
        if (body == null || body.isNull()) {
            throw GoogleFailureTranslator.unavailable();
        }
        return body;
    }
}
