package com.mapmory.backend.common.handler;

import com.mapmory.backend.common.http.RequestPaths;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * 모든 ProblemDetail 응답의 {@code instance}를 경로 패턴으로 맞춘다.
 *
 * <p>우리 핸들러는 {@code ProblemDetailFactory}에서 이미 패턴을 넣는다. 그런데 405·415처럼 Spring이 직접 만드는
 * ProblemDetail은 {@code instance}가 비어 있으면 Spring이 원본 경로로 채운다. 이 advice는 그 뒤, 본문을 쓰기 직전에
 * 돌아 원본 경로의 경로 변수(초대 토큰 등)가 응답에 실리지 않게 한다.
 */
@RestControllerAdvice
public class ProblemDetailInstanceAdvice implements ResponseBodyAdvice<Object> {

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType,
                                  Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                  ServerHttpRequest request, ServerHttpResponse response) {
        if (body instanceof ProblemDetail problem && request instanceof ServletServerHttpRequest servletRequest) {
            problem.setInstance(RequestPaths.instanceOf(servletRequest.getServletRequest()));
        }
        return body;
    }
}
