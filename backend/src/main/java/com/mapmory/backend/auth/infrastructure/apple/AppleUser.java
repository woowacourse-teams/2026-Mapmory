package com.mapmory.backend.auth.infrastructure.apple;

/**
 * 검증을 마친 애플 identity token에서 우리가 쓰는 값만 꺼낸다.
 *
 * subject : 애플 계정 고유 식별자(sub). 같은 개발자 팀(Team ID) 안에서 바뀌지 않는다. provider_id로 사용한다.
 *           이메일은 "나의 이메일 가리기"로 중계 주소일 수 있고 바뀔 수 있어 쓰지 않는다.
 */
record AppleUser(
        String subject
) {
}
