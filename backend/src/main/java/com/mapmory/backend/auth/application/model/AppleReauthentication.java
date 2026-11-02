package com.mapmory.backend.auth.application.model;

/**
 * 탈퇴하려고 애플 로그인을 다시 한 결과. identity token을 검증해 얻는다.
 *
 * subject  : 재인증한 애플 계정(sub). 탈퇴하는 회원의 provider_id와 같아야 한다
 * clientId : 토큰이 발급된 앱(aud). authorization code를 같은 client_id로 교환해야 한다
 */
public record AppleReauthentication(
        String subject,
        String clientId
) {
}
