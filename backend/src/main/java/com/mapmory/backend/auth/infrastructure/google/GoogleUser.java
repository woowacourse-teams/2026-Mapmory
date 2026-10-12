package com.mapmory.backend.auth.infrastructure.google;

/**
 * 검증을 마친 구글 ID token에서 우리가 쓰는 값만 꺼낸다.
 *
 * subject : 구글 계정 고유 식별자(sub). provider_id로 사용한다. 이메일은 바뀔 수 있어 쓰지 않는다.
 * name    : 표시 이름. profile 범위에 동의하지 않으면 없을 수 있다.
 */
record GoogleUser(
        String subject,
        String name
) {
}
