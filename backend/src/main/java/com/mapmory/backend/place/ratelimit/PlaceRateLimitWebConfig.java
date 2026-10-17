package com.mapmory.backend.place.ratelimit;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 장소 API마다 어떤 회원 한도를 확인할지 정한다. 한도를 어떻게 세는지는 {@link MemberPlaceRateLimiter}가 맡는다.
 *
 * <p>WebMvcConfigurer는 @WebMvcTest 슬라이스에도 들어간다. 한도 구현체는 DB가 필요해 슬라이스에 없으므로
 * {@code @Lazy}로 첫 요청 때 찾는다.
 */
@Configuration
class PlaceRateLimitWebConfig implements WebMvcConfigurer {

    private final MemberPlaceRateLimiter rateLimiter;

    PlaceRateLimitWebConfig(@Lazy MemberPlaceRateLimiter rateLimiter) {
        this.rateLimiter = rateLimiter;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new PlaceRateLimitInterceptor(rateLimiter::checkSearch))
                .addPathPatterns("/api/v1/places/search");
        registry.addInterceptor(new PlaceRateLimitInterceptor(rateLimiter::checkSelection))
                .addPathPatterns("/api/v1/places/*")
                .excludePathPatterns("/api/v1/places/search");
    }
}
