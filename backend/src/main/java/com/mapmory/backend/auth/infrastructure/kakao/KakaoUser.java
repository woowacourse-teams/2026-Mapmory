package com.mapmory.backend.auth.infrastructure.kakao;

/**
 * 검증을 마친 카카오 ID token에서 우리가 쓰는 값만 꺼낸다.
 *
 * subject  : 카카오 회원번호(sub). provider_id로 사용한다.
 * nickname : 닉네임. 사용자가 동의하지 않으면 없을 수 있다.
 */
record KakaoUser(
        String subject,
        String nickname
) {
}
