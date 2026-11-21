package com.mapmory.backend.common.handler;

import com.mapmory.backend.common.ProblemDetailFactory;
import com.mapmory.backend.common.http.RequestPaths;
import com.mapmory.backend.common.exception.FieldErrorDetail;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@Order(Ordered.HIGHEST_PRECEDENCE + 1)
@RestControllerAdvice
public class ValidationExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ValidationExceptionHandler.class);

    private final ProblemDetailFactory problemDetailFactory;

    public ValidationExceptionHandler(ProblemDetailFactory problemDetailFactory) {
        this.problemDetailFactory = problemDetailFactory;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleBodyValidation(
            MethodArgumentNotValidException exception,
            HttpServletRequest request
    ) {
        List<FieldErrorDetail> errors = exception.getBindingResult().getFieldErrors().stream()
                .map(ValidationExceptionHandler::toFieldErrorDetail)
                .toList();
        return problemDetailFactory.validation(errors, request);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ProblemDetail> handleMethodValidation(
            HandlerMethodValidationException exception,
            HttpServletRequest request
    ) {
        if (exception.isForReturnValue()) {
            String uri = RequestPaths.patternOf(request);
            log.atError()
                    .addKeyValue("event", "RESPONSE_VALIDATION_FAILED")
                    .addKeyValue("status", 500)
                    .addKeyValue("httpMethod", request.getMethod())
                    .addKeyValue("uri", uri)
                    .setCause(exception)
                    .log("Controller return value validation failed: method={}, uri={}",
                            request.getMethod(), uri);
            return problemDetailFactory.internalServerError(request);
        }

        Stream<FieldErrorDetail> parameterErrors = exception.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream().map(error -> new FieldErrorDetail(
                        parameterName(result.getMethodParameter().getParameterName(),
                                result.getMethodParameter().getParameterIndex()),
                        message(error)
                )));
        Stream<FieldErrorDetail> crossParameterErrors = exception.getCrossParameterValidationResults().stream()
                .map(error -> new FieldErrorDetail("arguments", message(error)));

        List<FieldErrorDetail> errors = Stream.concat(parameterErrors, crossParameterErrors)
                .toList();
        return problemDetailFactory.validation(errors, request);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ProblemDetail> handleConstraintViolation(
            ConstraintViolationException exception,
            HttpServletRequest request
    ) {
        List<FieldErrorDetail> errors = exception.getConstraintViolations().stream()
                .map(violation -> new FieldErrorDetail(
                        violation.getPropertyPath().toString(),
                        violation.getMessage()
                ))
                .toList();
        return problemDetailFactory.validation(errors, request);
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ProblemDetail> handleMissingRequestHeader(
            MissingRequestHeaderException exception,
            HttpServletRequest request
    ) {
        return problemDetailFactory.validation(
                List.of(new FieldErrorDetail(exception.getHeaderName(), "필수 요청 헤더입니다.")),
                request
        );
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ProblemDetail> handleArgumentTypeMismatch(
            MethodArgumentTypeMismatchException exception,
            HttpServletRequest request
    ) {
        return problemDetailFactory.validation(
                List.of(new FieldErrorDetail(exception.getName(), "올바른 형식의 값이어야 합니다.")),
                request
        );
    }

    private static FieldErrorDetail toFieldErrorDetail(FieldError error) {
        return new FieldErrorDetail(error.getField(), message(error));
    }

    private static String message(MessageSourceResolvable error) {
        if(error.getDefaultMessage() == null) {
            return "유효하지 않은 값입니다.";
        }
        return error.getDefaultMessage();
    }

    private static String parameterName(String name, int index) {
        if(name == null) {
            return "arg" + index;
        }
        return name;
    }
}
