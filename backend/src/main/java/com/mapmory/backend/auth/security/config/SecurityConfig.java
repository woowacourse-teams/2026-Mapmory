package com.mapmory.backend.auth.security.config;

import com.mapmory.backend.auth.exception.AuthErrorCode;
import com.mapmory.backend.auth.security.filter.JwtAuthenticationFilter;
import com.mapmory.backend.auth.security.handler.RestAccessDeniedHandler;
import com.mapmory.backend.auth.security.handler.RestAuthenticationEntryPoint;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.security.autoconfigure.actuate.web.servlet.EndpointRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final RestAuthenticationEntryPoint authenticationEntryPoint;
    private final RestAccessDeniedHandler accessDeniedHandler;
    private final SecurityContextRepository securityContextRepository;

    public SecurityConfig(
            JwtAuthenticationFilter jwtAuthenticationFilter,
            RestAuthenticationEntryPoint authenticationEntryPoint,
            RestAccessDeniedHandler accessDeniedHandler,
            SecurityContextRepository securityContextRepository
    ) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.authenticationEntryPoint = authenticationEntryPoint;
        this.accessDeniedHandler = accessDeniedHandler;
        this.securityContextRepository = securityContextRepository;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        HttpSessionCsrfTokenRepository csrfTokenRepository = new HttpSessionCsrfTokenRepository();
        http
                .cors(Customizer.withDefaults())
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfTokenRepository)
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                        .ignoringRequestMatchers(request -> !isAdminRequest(request)))
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .securityContext(context -> context.securityContextRepository(securityContextRepository))
                .authorizeHttpRequests(authorize -> authorize
                        // Security 6+는 ERROR 디스패치에도 인가를 적용한다. 이를 허용하지 않으면
                        // permitAll 경로에서 예외가 나 /error로 재디스패치될 때 401로 덮여
                        // 원래 에러 응답(404/500 등)이 가려진다.
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(EndpointRequest.to("health", "prometheus")).permitAll()
                        .requestMatchers("/health").permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        // /auth 아래 새 경로가 실수로 공개되지 않도록 인증 없이 여는 경로를 하나씩 적는다.
                        .requestMatchers(HttpMethod.POST,
                                "/api/v1/auth/login/kakao", "/api/v1/auth/login/google", "/api/v1/auth/login/apple")
                        .access(rejectingExpiredAccessToken())
                        .requestMatchers(HttpMethod.POST,
                                "/api/v1/auth/login/guest", "/api/v1/auth/token/refresh", "/api/v1/auth/logout").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/waitlist").permitAll()
                        .requestMatchers("/api/v1/admin/auth/csrf", "/api/v1/admin/auth/login").permitAll()
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .exceptionHandling(handler -> handler
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /**
     * 소셜 로그인은 인증 없이 열지만, 만료된 access token이 오면 401 EXPIRED_ACCESS_TOKEN으로 막는다.
     *
     * 게스트 토큰이 함께 오면 그 게스트를 승격한다(ADR 0015). 만료된 토큰을 "게스트 없음"으로 통과시키면
     * 승격 대신 새 회원이 생기고 게스트 기록이 이어지지 않는데, 응답이 200이라 앱은 알 수 없다.
     * 401을 주면 앱이 재발급한 뒤 다시 시도해 승격된다. 거부된 익명 요청은 entry point가 필터가 남긴 코드로 응답한다.
     * 위조·형식 오류 토큰은 재발급으로 복구되지 않아 막으면 로그인 자체가 안 되므로 지금처럼 통과시킨다. (#453)
     */
    private static AuthorizationManager<RequestAuthorizationContext> rejectingExpiredAccessToken() {
        return (authentication, context) -> new AuthorizationDecision(
                context.getRequest().getAttribute(JwtAuthenticationFilter.AUTH_ERROR_ATTRIBUTE)
                        != AuthErrorCode.EXPIRED_ACCESS_TOKEN);
    }

    private static boolean isAdminRequest(HttpServletRequest request) {
        return request.getRequestURI().startsWith("/api/v1/admin/");
    }
}
