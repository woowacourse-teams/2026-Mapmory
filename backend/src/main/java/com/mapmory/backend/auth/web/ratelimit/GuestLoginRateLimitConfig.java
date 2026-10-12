package com.mapmory.backend.auth.web.ratelimit;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 게스트 로그인 제한을 구성한다.
 *
 * limiter를 이 설정의 @Bean으로 두어 자족적으로 만든다. WebMvcConfigurer는 @WebMvcTest
 * 슬라이스에도 포함되는데, 외부 @Component에 의존하면 슬라이스에서 컨텍스트가 뜨지 않는다.
 */
@Configuration
@EnableConfigurationProperties(GuestLoginRateLimitProperties.class)
public class GuestLoginRateLimitConfig implements WebMvcConfigurer {

    private final GuestLoginRateLimiter rateLimiter;

    public GuestLoginRateLimitConfig(GuestLoginRateLimitProperties properties) {
        this.rateLimiter = new GuestLoginRateLimiter(properties);
    }

    @Bean
    public GuestLoginRateLimiter guestLoginRateLimiter() {
        return rateLimiter;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new GuestLoginRateLimitInterceptor(rateLimiter))
                .addPathPatterns("/api/v1/auth/login/guest");
    }
}
