package com.mapmory.backend.member.web.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * nickname : 앞뒤 공백을 지운 뒤 1~20자(코드 포인트). 세부 규칙은 MemberName.ofNickname이 확인한다.
 */
public record NicknameChangeRequest(
        @NotBlank
        String nickname
) {
}
