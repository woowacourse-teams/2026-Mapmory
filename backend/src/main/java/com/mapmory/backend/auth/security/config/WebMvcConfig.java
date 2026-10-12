package com.mapmory.backend.auth.security.config;

import com.mapmory.backend.auth.security.resolver.LoginMemberArgumentResolver;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final LoginMemberArgumentResolver loginMemberArgumentResolver;
    private final String[] waitlistAllowedOrigins;
    private final String[] adminAllowedOrigins;

    public WebMvcConfig(
            LoginMemberArgumentResolver loginMemberArgumentResolver,
            @Value("${waitlist.cors.allowed-origins}") String[] waitlistAllowedOrigins,
            @Value("${admin.cors.allowed-origins}") String[] adminAllowedOrigins
    ) {
        this.loginMemberArgumentResolver = loginMemberArgumentResolver;
        this.waitlistAllowedOrigins = waitlistAllowedOrigins;
        this.adminAllowedOrigins = adminAllowedOrigins;
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(loginMemberArgumentResolver);
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/v1/waitlist")
                .allowedOrigins(waitlistAllowedOrigins)
                .allowedMethods("POST")
                .allowedHeaders("Content-Type")
                .maxAge(3600);
        registry.addMapping("/api/v1/admin/**")
                .allowedOrigins(adminAllowedOrigins)
                .allowedMethods("GET", "POST", "PATCH", "OPTIONS")
                .allowedHeaders("Content-Type", "Authorization", "X-CSRF-TOKEN")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
