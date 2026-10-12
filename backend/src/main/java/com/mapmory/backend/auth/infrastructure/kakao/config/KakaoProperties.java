package com.mapmory.backend.auth.infrastructure.kakao.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "kakao")
public record KakaoProperties(
        String userInfoUri
) {
}
