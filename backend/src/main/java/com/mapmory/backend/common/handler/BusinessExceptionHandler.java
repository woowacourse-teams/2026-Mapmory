package com.mapmory.backend.common.handler;

import com.mapmory.backend.common.ProblemDetailFactory;
import com.mapmory.backend.common.exception.BusinessException;
import com.mapmory.backend.common.exception.ErrorKind;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

        log.debug("Business exception: code={}, status={}, method={}, uri={}",
                exception.getErrorCode().code(),
                status.value(),
                request.getMethod(),
                request.getRequestURI());

        return problemDetailFactory.from(
                status,
                exception.getErrorCode(),
                exception.getDetail(),
                request
        );
    }

    private static HttpStatus toHttpStatus(ErrorKind kind) {
        return switch (kind) {
            case INVALID_INPUT -> HttpStatus.BAD_REQUEST;
            case AUTHENTICATION_REQUIRED -> HttpStatus.UNAUTHORIZED;
            case ACCESS_DENIED -> HttpStatus.FORBIDDEN;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case CONFLICT -> HttpStatus.CONFLICT;
            case SERVICE_UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
        };
    }
}
