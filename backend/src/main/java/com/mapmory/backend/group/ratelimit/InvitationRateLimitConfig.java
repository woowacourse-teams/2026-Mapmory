package com.mapmory.backend.group.ratelimit;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 초대 미리보기(GET)와 수락(POST .../accept)에 회원별 한도를 건다. 초대 만들기는 그룹 멤버만 할 수 있어 걸지 않는다.
 *
 * <p>limiter를 이 설정의 @Bean으로 두어 @WebMvcTest 슬라이스에서도 컨텍스트가 뜨게 한다(게스트 로그인 한도와 같은 방식).
 */
@Configuration
@EnableConfigurationProperties(InvitationRateLimitProperties.class)
class InvitationRateLimitConfig implements WebMvcConfigurer {

    private final InvitationRateLimitProperties properties;

    InvitationRateLimitConfig(InvitationRateLimitProperties properties) {
        this.properties = properties;
    }

    @Bean
    MemberInvitationRateLimiter memberInvitationRateLimiter() {
        return new MemberInvitationRateLimiter(properties);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new InvitationRateLimitInterceptor(memberInvitationRateLimiter()))
                .addPathPatterns("/api/v1/invitations/*", "/api/v1/invitations/*/accept");
    }
}
