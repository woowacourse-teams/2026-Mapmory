package com.mapmory.backend.auth.token.jwt;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(
        String secret,
        Duration accessTokenValidity,
        Duration refreshTokenValidity,
        // 게스트는 로그인 수단이 없어 refresh 만료 시 복구할 방법이 없으므로 별도로 길게 둔다. (ADR 0015)
        Duration guestRefreshTokenValidity
) {
}
