package com.mapmory.backend.auth;

/**
 * 발급된 access·refresh 토큰 쌍.
 *
 * <p>표현 형식을 정하지 않으므로 응답 DTO 변환은 웹 계층이 맡는다. (ADR 0016, 0017)
 */
public record AuthTokens(
        String accessToken,
        String refreshToken
) {
}
