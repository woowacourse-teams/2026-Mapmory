package com.mapmory.backend.common.handler;

import com.mapmory.backend.auth.exception.AuthErrorCode;
import com.mapmory.backend.common.ProblemDetailFactory;
import com.mapmory.backend.common.exception.BusinessException;
import com.mapmory.backend.common.exception.ErrorCode;
import com.mapmory.backend.common.exception.ErrorKind;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.spi.LoggingEventBuilder;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class BusinessExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(BusinessExceptionHandler.class);

    private final ProblemDetailFactory problemDetailFactory;

    public BusinessExceptionHandler(ProblemDetailFactory problemDetailFactory) {
        this.problemDetailFactory = problemDetailFactory;
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ProblemDetail> handle(
            BusinessException exception,
            HttpServletRequest request
    ) {
        HttpStatus status = toHttpStatus(exception.getErrorCode().kind());

        log(exception, status, request);

        return problemDetailFactory.from(
                status,
                exception.getErrorCode(),
                exception.getDetail(),
                request
        );
    }

    private static void log(
            BusinessException exception,
            HttpStatus status,
            HttpServletRequest request
    ) {
        ErrorCode errorCode = exception.getErrorCode();
        // TODO: SERVICE_UNAVAILABLE 전체를 ERROR로 기록할지, KAKAO_UNAVAILABLE만 대상으로 할지 논의 필요
        boolean serviceUnavailable = errorCode.kind() == ErrorKind.SERVICE_UNAVAILABLE;
        boolean refreshTokenFailure = errorCode == AuthErrorCode.EXPIRED_REFRESH_TOKEN
                || errorCode == AuthErrorCode.INVALID_REFRESH_TOKEN;
        LoggingEventBuilder event = serviceUnavailable
                ? log.atError()
                : refreshTokenFailure ? log.atInfo() : log.atDebug();

        event.addKeyValue("event", "BUSINESS_EXCEPTION")
                .addKeyValue("errorCode", errorCode.code())
                .addKeyValue("status", status.value())
                .addKeyValue("httpMethod", request.getMethod())
                .addKeyValue("uri", request.getRequestURI());
        if (serviceUnavailable) {
            event.setCause(exception);
        }

        event.log("Business exception: code={}, status={}, method={}, uri={}",
                errorCode.code(),
                status.value(),
                request.getMethod(),
                request.getRequestURI());
    }

    private static HttpStatus toHttpStatus(ErrorKind kind) {
        return switch (kind) {
            case INVALID_INPUT -> HttpStatus.BAD_REQUEST;
            case AUTHENTICATION_REQUIRED -> HttpStatus.UNAUTHORIZED;
            case ACCESS_DENIED -> HttpStatus.FORBIDDEN;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case CONFLICT -> HttpStatus.CONFLICT;
            case TOO_MANY_REQUESTS -> HttpStatus.TOO_MANY_REQUESTS;
            case SERVICE_UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
        };
    }
}
