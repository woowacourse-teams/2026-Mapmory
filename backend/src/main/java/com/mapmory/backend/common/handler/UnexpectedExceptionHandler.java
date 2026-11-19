package com.mapmory.backend.common.handler;

import com.mapmory.backend.common.ProblemDetailFactory;
import com.mapmory.backend.common.http.RequestPaths;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Order(Ordered.LOWEST_PRECEDENCE)
@RestControllerAdvice
public class UnexpectedExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(UnexpectedExceptionHandler.class);

    private final ProblemDetailFactory problemDetailFactory;

    public UnexpectedExceptionHandler(ProblemDetailFactory problemDetailFactory) {
        this.problemDetailFactory = problemDetailFactory;
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handle(
            Exception exception,
            HttpServletRequest request
    ) {
        String uri = RequestPaths.patternOf(request);
        log.atError()
                .addKeyValue("event", "UNHANDLED_EXCEPTION")
                .addKeyValue("status", 500)
                .addKeyValue("httpMethod", request.getMethod())
                .addKeyValue("uri", uri)
                .setCause(exception)
                .log("Unhandled exception while processing {} {}",
                        request.getMethod(), uri);
        return problemDetailFactory.internalServerError(request);
    }
}
