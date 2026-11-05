package com.mapmory.backend.place.infrastructure.google;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import org.apache.hc.client5.http.ConnectTimeoutException;
import org.apache.hc.core5.http.ConnectionRequestTimeoutException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;

/** Google 호출 실패를 읽는 규칙. 장소 검색과 역지오코딩 클라이언트가 같이 쓴다. */
final class GoogleFailures {

    private GoogleFailures() {
    }

    /**
     * 타임아웃이 어느 단계였는지 본다. POOL이면 우리 풀에서 연결을 못 받은 것이고, CONNECT면 Google에 닿지 못한 것이고,
     * RESPONSE면 Google이 늦게 답한 것이다. 타임아웃이 아니면 null이다. Apache의 연결 타임아웃은 응답 타임아웃
     * (SocketTimeoutException)을 상속하므로 연결을 먼저 가린다.
     */
    static String timeoutPhase(RestClientException exception) {
        if (!(exception instanceof ResourceAccessException)) {
            return null;
        }
        for (Throwable cause = exception.getCause(); cause != null; cause = cause.getCause()) {
            if (cause instanceof ConnectionRequestTimeoutException) {
                return "POOL";
            }
            if (cause instanceof ConnectTimeoutException) {
                return "CONNECT";
            }
            if (cause instanceof SocketTimeoutException) {
                return "RESPONSE";
            }
        }
        return null;
    }

    /**
     * Google이 과금하지 않는 실패인지 본다. 5xx는 Google이 처리하지 못한 것이고, 연결하지 못한 요청은 Google에 닿지 않았다.
     * 풀에서 연결을 못 받은 요청도 보내지 않았다. 연결한 뒤 답을 기다리다 끊긴 요청은 Google이 처리했을 수 있어
     * 과금된 것으로 본다.
     */
    static boolean notBilled(RestClientException exception) {
        if (exception instanceof HttpServerErrorException) {
            return true;
        }
        if (!(exception instanceof ResourceAccessException)) {
            return false;
        }
        for (Throwable cause = exception.getCause(); cause != null; cause = cause.getCause()) {
            if (cause instanceof ConnectException || cause instanceof ConnectTimeoutException
                    || cause instanceof ConnectionRequestTimeoutException) {
                return true;
            }
        }
        return false;
    }
}
