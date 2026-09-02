package com.mapmory.backend.auth;

/**
 * 로그인 결과. 토큰 쌍과 신규 가입 여부를 담는다.
 *
 * <p>{@code newMember}는 온보딩 분기에 쓰인다. 게스트 승격은 이미 앱을 사용한 상태이므로
 * 신규로 보지 않는다. (ADR 0015)
 */
public record LoginResult(
        AuthTokens tokens,
        boolean newMember
) {
}
