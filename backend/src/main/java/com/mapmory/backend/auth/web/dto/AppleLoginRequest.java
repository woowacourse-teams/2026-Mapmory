package com.mapmory.backend.auth.web.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * identityToken : 앱이 Sign in with Apple로 받은 identity token(JWT)
 *
 * 애플은 토큰에 이름을 넣지 않으므로 애플 회원은 기본 이름으로 시작한다. (ADR 0020)
 */
public record AppleLoginRequest(
        @NotBlank
        String identityToken
) {
}
