package com.mapmory.backend.auth.web.ratelimit;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 게스트 로그인 남용 방지 설정.
 *
 * capacity : window 동안 한 출처가 만들 수 있는 게스트 수
 * window   : 한도를 세는 기간
 */
@ConfigurationProperties(prefix = "guest-login.rate-limit")
public record GuestLoginRateLimitProperties(
        int capacity,
        Duration window
) {
}
