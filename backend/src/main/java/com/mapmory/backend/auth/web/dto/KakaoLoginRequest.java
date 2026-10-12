package com.mapmory.backend.auth.web.dto;

import jakarta.validation.constraints.NotBlank;

public record KakaoLoginRequest(
        @NotBlank
        String kakaoAccessToken
) {
}
