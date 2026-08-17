package com.mapmory.backend.auth.dto;

/**
 * 로그인 응답.
 *
 * 클라이언트는 accessToken(단기)과 refreshToken(장기)을 저장한다.
 * access 만료 시 refreshToken으로 재발급한다.
 * 회원 식별자(memberId 등)는 노출하지 않는다. ("내 데이터" 통신은 토큰만으로 충분)
 * isNewMember는 신규 가입 여부로, 온보딩 분기 등에 쓸 수 있다.
 */
public record LoginResponse(
        String accessToken,
        String refreshToken,
        boolean isNewMember
) {
}
