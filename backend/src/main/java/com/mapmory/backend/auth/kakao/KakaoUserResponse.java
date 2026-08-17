package com.mapmory.backend.auth.kakao;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 카카오 GET /v2/user/me 응답 중 우리가 쓰는 필드만 매핑한다.
 *
 * id            : 카카오 회원번호 (provider_id로 사용)
 * kakao_account : 닉네임 등 계정 정보. 사용자가 동의하지 않으면 하위 값이 없을 수 있다.
 */
public record KakaoUserResponse(
        Long id,
        @JsonProperty("kakao_account") KakaoAccount kakaoAccount
) {

    public record KakaoAccount(Profile profile) {

        public record Profile(String nickname) {
        }
    }

    public String nickname() {
        if (kakaoAccount == null || kakaoAccount.profile() == null) {
            return null;
        }
        return kakaoAccount.profile().nickname();
    }
}
