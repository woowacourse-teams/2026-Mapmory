package com.mapmory.backend.common.handler;

import com.mapmory.backend.common.ProblemDetailFactory;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 매핑되는 API가 없는 경로의 404를 고정 문구로 응답한다.
 *
 * <p>Spring 기본 응답은 detail에 {@code "No static resource <경로>."}를 넣는다. 경로에 초대 토큰 같은 비밀값이 있으면
 * 그대로 실린다(예: {@code /api/v1/invitations/<토큰>/unknown}). Boot의 기본 핸들러({@code @Order(0)})보다 먼저 받는다.
 */
@Order(Ordered.HIGHEST_PRECEDENCE + 2)
@RestControllerAdvice
public class NoResourceFoundExceptionHandler {

    private final ProblemDetailFactory problemDetailFactory;

    public NoResourceFoundExceptionHandler(ProblemDetailFactory problemDetailFactory) {
        this.problemDetailFactory = problemDetailFactory;
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ProblemDetail> handle(NoResourceFoundException exception, HttpServletRequest request) {
        return problemDetailFactory.notFound(request);
    }
}
