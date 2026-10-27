package com.mapmory.backend.auth.application.model;

/**
 * 로그인 결과. 토큰 쌍과 신규 가입 여부, 게스트를 두고 떠났는지를 담는다.
 *
 * <p>{@code newMember}는 온보딩 분기에 쓰인다. 게스트 승격은 이미 앱을 사용한 상태이므로
 * 신규로 보지 않는다. (ADR 0015)
 *
 * <p>{@code guestAbandoned}는 게스트로 사용하다 이미 가입한 소셜 계정으로 로그인해 게스트 기록이
 * 이어지지 않았을 때 true다. 로그인 전에는 기존 계정이 있는지 알 수 없으므로 로그인 뒤에 알린다. (ADR 0021)
 */
public record LoginResult(
        AuthTokens tokens,
        boolean newMember,
        boolean guestAbandoned
) {
}
