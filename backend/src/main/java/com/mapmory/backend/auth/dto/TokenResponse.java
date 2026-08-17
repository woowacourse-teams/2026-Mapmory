package com.mapmory.backend.auth.dto;

/**
 * 토큰 재발급 응답. 회전으로 새로 발급된 access/refresh 쌍을 반환한다.
 */
public record TokenResponse(
        String accessToken,
        String refreshToken
) {
}
