package com.mapmory.backend.auth.web.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 애플 회원 탈퇴 요청. 앱이 탈퇴 직전에 Sign in with Apple을 다시 해 받은 값을 보낸다. (ADR 0025)
 */
public record AppleWithdrawalRequest(
        @NotBlank
        String identityToken,
        @NotBlank
        String authorizationCode
) {
}
