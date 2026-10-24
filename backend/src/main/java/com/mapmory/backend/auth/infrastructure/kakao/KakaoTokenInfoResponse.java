package com.mapmory.backend.auth.infrastructure.kakao;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 카카오 GET /v1/user/access_token_info 응답 중 우리가 쓰는 필드만 매핑한다.
 *
 * app_id : 토큰이 발급된 카카오 앱 ID
 */
record KakaoTokenInfoResponse(
        @JsonProperty("app_id") Long appId
) {
}
