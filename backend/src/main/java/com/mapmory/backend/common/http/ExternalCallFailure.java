package com.mapmory.backend.common.http;

import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.net.ssl.SSLHandshakeException;
import org.apache.hc.client5.http.ConnectTimeoutException;
import org.apache.hc.core5.http.ConnectionRequestTimeoutException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * 외부 API 호출 하나가 어떻게 실패했는지 읽는다. {@code RestClient}로 부르는 외부 API면 어디서나 같은 기준으로 판단한다.
 *
 * <ul>
 *   <li>{@link #rejected()}: 상대가 4xx로 답했다. 키 거절처럼 고칠 때까지 계속되는 실패가 많다.</li>
 *   <li>{@link #serverError()}: 상대가 5xx로 답했다. 상대 쪽 장애다.</li>
 *   <li>{@link #timeoutPhase()}: 어느 단계에서 시간이 다 됐는지.</li>
 *   <li>{@link #sent()}: 요청이 상대에게 나갔는지. 나가지 않았으면 상대는 처리하지 않았다.</li>
 * </ul>
 *
 * <p>과금이나 재시도처럼 이 사실을 어떻게 해석할지는 API마다 달라 각 클라이언트가 정한다.
 */
public final class ExternalCallFailure {

    private final RestClientException exception;

    private ExternalCallFailure(RestClientException exception) {
        this.exception = exception;
    }

    public static ExternalCallFailure of(RestClientException exception) {
        return new ExternalCallFailure(exception);
    }

    public boolean rejected() {
        return response().map(response -> response.getStatusCode().is4xxClientError()).orElse(false);
    }

    public boolean serverError() {
        return response().map(response -> response.getStatusCode().is5xxServerError()).orElse(false);
    }

    /** HTTP 상태 코드. 상대가 답하지 않았으면(타임아웃·연결 실패) 0이다. */
    public int status() {
        return response().map(response -> response.getStatusCode().value()).orElse(0);
    }

    public String responseBody() {
        return response().map(RestClientResponseException::getResponseBodyAsString).orElse("");
    }

    /**
     * 시간이 다 된 단계. POOL이면 우리 연결 풀에서 연결을 못 받은 것이고, CONNECT면 상대에게 닿지 못한 것이고,
     * RESPONSE면 상대가 늦게 답한 것이다. Apache의 연결 타임아웃은 응답 타임아웃(SocketTimeoutException)을
     * 상속하므로 연결을 먼저 가린다.
     */
    public Optional<TimeoutPhase> timeoutPhase() {
        for (Throwable cause : causes()) {
            if (cause instanceof ConnectionRequestTimeoutException) {
                return Optional.of(TimeoutPhase.POOL);
            }
            if (cause instanceof ConnectTimeoutException) {
                return Optional.of(TimeoutPhase.CONNECT);
            }
            if (cause instanceof SocketTimeoutException) {
                return Optional.of(TimeoutPhase.RESPONSE);
            }
        }
        return Optional.empty();
    }

    /**
     * 요청이 상대에게 나갔는지. 주소를 찾지 못했거나(DNS) 연결·TLS 핸드셰이크에 실패했거나 풀에서 연결을 못 받은
     * 요청은 나가지 않았다. 연결한 뒤 답을 기다리다 끊긴 요청은 상대가 처리했을 수 있어 나간 것으로 본다.
     */
    public boolean sent() {
        for (Throwable cause : causes()) {
            if (cause instanceof ConnectException || cause instanceof ConnectTimeoutException
                    || cause instanceof ConnectionRequestTimeoutException
                    || cause instanceof UnknownHostException || cause instanceof NoRouteToHostException
                    || cause instanceof SSLHandshakeException) {
                return false;
            }
        }
        return true;
    }

    private Optional<RestClientResponseException> response() {
        if (exception instanceof RestClientResponseException response) {
            return Optional.of(response);
        }
        return Optional.empty();
    }

    // 연결·응답 실패는 ResourceAccessException 아래 원인으로 온다.
    private List<Throwable> causes() {
        if (!(exception instanceof ResourceAccessException)) {
            return List.of();
        }
        List<Throwable> causes = new ArrayList<>();
        for (Throwable cause = exception.getCause(); cause != null; cause = cause.getCause()) {
            causes.add(cause);
        }
        return causes;
    }

    public enum TimeoutPhase {
        POOL, CONNECT, RESPONSE
    }
}
