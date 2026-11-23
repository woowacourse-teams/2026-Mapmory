package com.mapmory.backend.common.cache;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.security.autoconfigure.actuate.web.servlet.EndpointRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;

@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class DeploymentCacheSecurityConfig {

    @Bean
    @Order(0)
    public SecurityFilterChain deploymentCacheSecurityFilterChain(HttpSecurity http, Environment environment)
            throws Exception {
        return http
                .securityMatcher(EndpointRequest.to("deploymentcache"))
                .authorizeHttpRequests(authorize -> authorize.anyRequest().access((authentication, context) ->
                        new AuthorizationDecision(isLocalManagementRequest(context.getRequest(), environment))))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(cache -> cache.disable())
                .exceptionHandling(handler -> handler
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.FORBIDDEN)))
                .build();
    }

    static boolean isLocalManagementRequest(HttpServletRequest request, Environment environment) {
        Integer managementPort = environment.getProperty("local.management.port", Integer.class);
        Integer applicationPort = environment.getProperty("local.server.port", Integer.class);
        String bindAddress = environment.getProperty("management.server.address");
        String remoteAddress = request.getRemoteAddr();
        // getLocalPort는 프록시가 보낸 Host/X-Forwarded-Port가 아닌 실제 수신 포트다.
        // 관리 포트를 따로 두지 않은 환경에서는 인증 여부와 무관하게 거부한다.
        return managementPort != null && applicationPort != null
                && managementPort > 0 && applicationPort > 0
                && !managementPort.equals(applicationPort)
                && request.getLocalPort() == managementPort
                && isLoopback(bindAddress) && isLoopback(remoteAddress);
    }

    private static boolean isLoopback(String address) {
        return "127.0.0.1".equals(address) || "::1".equals(address)
                || "0:0:0:0:0:0:0:1".equals(address);
    }
}
