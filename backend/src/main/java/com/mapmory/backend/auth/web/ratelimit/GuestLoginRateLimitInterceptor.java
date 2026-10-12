package com.mapmory.backend.auth.web.ratelimit;

import com.mapmory.backend.auth.exception.AuthErrorCode;
import com.mapmory.backend.common.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 게스트 로그인 요청을 출처별로 제한한다.
 *
 * 컨트롤러 진입 전에 BusinessException을 던지며, 인터셉터 예외는 DispatcherServlet이
 * HandlerExceptionResolver로 넘기므로 기존 ProblemDetails 응답 형식이 그대로 유지된다.
 */
public class GuestLoginRateLimitInterceptor implements HandlerInterceptor {

    private static final String FORWARDED_FOR = "X-Forwarded-For";

    private final GuestLoginRateLimiter rateLimiter;

    public GuestLoginRateLimitInterceptor(GuestLoginRateLimiter rateLimiter) {
        this.rateLimiter = rateLimiter;
    }

    @Override
    public boolean preHandle(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler
    ) {
        if (!rateLimiter.tryAcquire(clientIp(request))) {
            throw new BusinessException(AuthErrorCode.GUEST_LOGIN_RATE_LIMITED);
        }
        return true;
    }

    /**
     * 운영에서는 nginx를 거치므로 remoteAddr이 항상 프록시 주소가 된다.
     * X-Forwarded-For의 첫 값(원 요청자)을 우선 사용한다.
     */
    private String clientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader(FORWARDED_FOR);
        if (forwardedFor == null || forwardedFor.isBlank()) {
            return request.getRemoteAddr();
        }
        return forwardedFor.split(",")[0].trim();
    }
}
