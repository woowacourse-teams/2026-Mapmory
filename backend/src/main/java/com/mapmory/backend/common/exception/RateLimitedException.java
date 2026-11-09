package com.mapmory.backend.common.exception;

import java.io.Serial;

/**
 * 한도에 걸린 요청. 언제 다시 할 수 있는지 응답의 {@code retryAfterSeconds}로 알린다.
 */
public class RateLimitedException extends BusinessException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final long retryAfterSeconds;

    public RateLimitedException(ErrorCode errorCode, long retryAfterSeconds) {
        super(errorCode);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
