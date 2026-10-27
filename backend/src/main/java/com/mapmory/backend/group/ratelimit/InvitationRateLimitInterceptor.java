package com.mapmory.backend.group.ratelimit;

import com.mapmory.backend.auth.exception.AuthErrorCode;
import com.mapmory.backend.common.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 컨트롤러에 들어가기 전에 회원별 초대 한도를 확인한다. 한도를 넘으면 BusinessException을 던지고,
 * DispatcherServlet이 기존 ProblemDetails 응답으로 바꾼다.
 */
class InvitationRateLimitInterceptor implements HandlerInterceptor {

    private final MemberInvitationRateLimiter rateLimiter;

    InvitationRateLimitInterceptor(MemberInvitationRateLimiter rateLimiter) {
        this.rateLimiter = rateLimiter;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 초대 API는 인증이 필요해 시큐리티 단계에서 이미 걸러지지만, 방어적으로 확인한다.
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Long memberId)) {
            throw new BusinessException(AuthErrorCode.INVALID_ACCESS_TOKEN);
        }
        rateLimiter.check(memberId);
        return true;
    }
}
