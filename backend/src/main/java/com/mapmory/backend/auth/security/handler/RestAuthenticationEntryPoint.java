package com.mapmory.backend.auth.security.handler;

import com.mapmory.backend.auth.exception.AuthErrorCode;
import com.mapmory.backend.admin.auth.AdminAuthErrorCode;
import com.mapmory.backend.auth.security.filter.JwtAuthenticationFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

/**
 * 인증되지 않은 요청(401)을 ProblemDetails로 응답한다.
 *
 * JWT 필터가 요청 속성에 남긴 실패 사유가 있으면 그 코드를, 없으면(토큰 미제공 등)
 * INVALID_ACCESS_TOKEN을 사용한다.
 */
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ProblemResponseWriter problemResponseWriter;

    public RestAuthenticationEntryPoint(ProblemResponseWriter problemResponseWriter) {
        this.problemResponseWriter = problemResponseWriter;
    }

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException
    ) throws IOException {
        if (request.getRequestURI().startsWith("/api/v1/admin/")) {
            problemResponseWriter.write(
                    request, response, HttpStatus.UNAUTHORIZED, AdminAuthErrorCode.ADMIN_SESSION_REQUIRED);
            return;
        }

        Object attribute = request.getAttribute(JwtAuthenticationFilter.AUTH_ERROR_ATTRIBUTE);
        AuthErrorCode errorCode = attribute instanceof AuthErrorCode code ? code : AuthErrorCode.INVALID_ACCESS_TOKEN;
        problemResponseWriter.write(request, response, HttpStatus.UNAUTHORIZED, errorCode);
    }
}
