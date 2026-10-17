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
 *
 * 인터셉터는 @Bean 메서드를 거쳐 컨테이너의 limiter를 받는다. 테스트가 limiter 빈을 바꾸면
 * 인터셉터도 바뀐 빈을 쓴다.
 */
@Configuration
@EnableConfigurationProperties(GuestLoginRateLimitProperties.class)
public class GuestLoginRateLimitConfig implements WebMvcConfigurer {

    private final GuestLoginRateLimitProperties properties;

    public GuestLoginRateLimitConfig(GuestLoginRateLimitProperties properties) {
        this.properties = properties;
    }

    @Bean
    public GuestLoginRateLimiter guestLoginRateLimiter() {
        return new GuestLoginRateLimiter(properties);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new GuestLoginRateLimitInterceptor(guestLoginRateLimiter()))
                .addPathPatterns("/api/v1/auth/login/guest");
    }
}
