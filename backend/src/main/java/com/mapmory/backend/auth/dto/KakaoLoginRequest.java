package com.mapmory.backend.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record KakaoLoginRequest(
        @NotBlank
        String kakaoAccessToken
) {
}
