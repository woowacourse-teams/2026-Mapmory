package com.mapmory.backend.auth.token.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Component;

/**
 * 우리 서비스의 access token 발급·검증.
 *
 * HS256 대칭키로 서명한다. 만료(ExpiredJwtException)와 그 외 위조/변조(JwtException)는
 * 호출부(JWT 인증 필터)에서 구분해 처리한다.
 */
@Component
public class JwtProvider {

    private final SecretKey key;
    private final Duration accessTokenValidity;
    private final Clock clock;

    public JwtProvider(JwtProperties properties, Clock clock) {
        this.key = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
        this.accessTokenValidity = properties.accessTokenValidity();
        this.clock = clock;
    }

    public String issueAccessToken(Long memberId) {
        Instant now = clock.instant();
        return Jwts.builder()
                .subject(String.valueOf(memberId))
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(accessTokenValidity)))
                .signWith(key)
                .compact();
    }

    public Long parseMemberId(String token) {
        // 만료 확인도 발급과 같은 시계로 한다.
        Claims claims = Jwts.parser()
                .clock(() -> Date.from(clock.instant()))
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return Long.valueOf(claims.getSubject());
    }
}
