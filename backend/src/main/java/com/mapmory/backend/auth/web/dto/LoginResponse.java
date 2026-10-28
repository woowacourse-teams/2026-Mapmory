package com.mapmory.backend.auth.web.dto;

import com.mapmory.backend.auth.application.model.LoginResult;

/**
 * 로그인 응답.
 *
 * 클라이언트는 accessToken(단기)과 refreshToken(장기)을 저장한다.
 * access 만료 시 refreshToken으로 재발급한다.
 * 회원 식별자(memberId 등)는 노출하지 않는다. ("내 데이터" 통신은 토큰만으로 충분)
 * isNewMember는 신규 가입 여부로, 온보딩 분기 등에 쓸 수 있다.
 * guestAbandoned는 게스트로 사용하다 이미 가입한 소셜 계정으로 로그인해 게스트 기록이 이어지지 않았을 때 true다.
 * 앱은 로그인 뒤 이 값으로 안내를 보여줄 수 있다. (ADR 0021)
 */
public record LoginResponse(
        String accessToken,
        String refreshToken,
        boolean isNewMember,
        boolean guestAbandoned
) {

    public static LoginResponse from(LoginResult result) {
        return new LoginResponse(
                result.tokens().accessToken(),
                result.tokens().refreshToken(),
                result.newMember(),
                result.guestAbandoned()
        );
    }
}
