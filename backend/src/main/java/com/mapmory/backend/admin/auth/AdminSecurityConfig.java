package com.mapmory.backend.admin.auth;

import com.mapmory.backend.admin.account.AdminAccountRepository;
import com.mapmory.backend.auth.security.ProblemResponseWriter;
import com.mapmory.backend.auth.security.RestAccessDeniedHandler;
import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class AdminSecurityConfig {

    private final AdminAccountRepository adminAccountRepository;
    private final ProblemResponseWriter problemResponseWriter;
    private final RestAccessDeniedHandler accessDeniedHandler;

    public AdminSecurityConfig(
            AdminAccountRepository adminAccountRepository,
            ProblemResponseWriter problemResponseWriter,
            RestAccessDeniedHandler accessDeniedHandler
    ) {
        this.adminAccountRepository = adminAccountRepository;
        this.problemResponseWriter = problemResponseWriter;
        this.accessDeniedHandler = accessDeniedHandler;
    }

    @Bean
    @Order(1)
    public SecurityFilterChain adminSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/api/v1/admin/**")
                .cors(Customizer.withDefaults())
                // 세션 쿠키 인증의 POST 요청에는 CSRF 토큰도 요구한다.
                .csrf(Customizer.withDefaults())
                .formLogin(config -> config.disable())
                .httpBasic(config -> config.disable())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .authorizeHttpRequests(authorize -> authorize
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.GET,
                                "/api/v1/admin/auth/csrf").permitAll()
                        .requestMatchers(HttpMethod.POST,
                                "/api/v1/admin/auth/login").permitAll()
                        .anyRequest().hasRole("ADMIN"))
                .exceptionHandling(handler -> handler
                        .authenticationEntryPoint((request, response, authException) ->
                                problemResponseWriter.write(
                                        request,
                                        response,
                                        HttpStatus.UNAUTHORIZED,
                                        AdminAuthErrorCode.ADMIN_SESSION_REQUIRED
                                ))
                        .accessDeniedHandler(accessDeniedHandler))
                .addFilterBefore(
                        new AdminSessionAuthenticationFilter(adminAccountRepository),
                        UsernamePasswordAuthenticationFilter.class
                );
        return http.build();
    }
}
