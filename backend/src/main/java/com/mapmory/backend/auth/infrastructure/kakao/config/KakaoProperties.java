package com.mapmory.backend.auth.infrastructure.kakao.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "kakao")
public record KakaoProperties(
        String tokenInfoUri,
        String userInfoUri,
        // 토큰이 이 앱에서 발급됐을 때만 로그인시킨다. 비어 있으면 모든 카카오 로그인을 거부한다. (ADR 0010)
        Long appId
) {
}
