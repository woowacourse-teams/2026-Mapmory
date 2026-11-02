package com.mapmory.backend.auth.application.port;

import com.mapmory.backend.auth.application.model.AppleReauthentication;

/**
 * 애플 회원이 탈퇴할 때 애플 토큰을 철회하는 포트. (ADR 0025)
 *
 * 재인증이 잘못됐으면 APPLE_REAUTHENTICATION_FAILED(400), 애플 장애·설정 오류면 APPLE_UNAVAILABLE(503)을 던진다.
 */
public interface AppleRevocationPort {

    /**
     * 앱이 다시 받은 identity token을 검증한다.
     */
    AppleReauthentication verify(String identityToken);

    /**
     * 재인증으로 받은 authorization code를 토큰으로 바꿔 철회한다. 앱과 애플 계정의 연결이 끊긴다.
     */
    void revoke(AppleReauthentication reauthentication, String authorizationCode);
}
