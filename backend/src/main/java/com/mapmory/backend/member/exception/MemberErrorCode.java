package com.mapmory.backend.member.exception;

import com.mapmory.backend.common.exception.ErrorCode;
import com.mapmory.backend.common.exception.ErrorKind;

public enum MemberErrorCode implements ErrorCode {
    MEMBER_NOT_FOUND(ErrorKind.NOT_FOUND, "MEMBER_NOT_FOUND", "회원을 찾을 수 없습니다.",
            "요청한 회원이 존재하지 않습니다."),
    // 본인 회원이 없거나 활동 중이 아니다. 남은 access token으로는 더 쓸 수 없으므로 앱이 로그아웃하도록 401로 응답한다.
    INACTIVE_MEMBER(ErrorKind.AUTHENTICATION_REQUIRED, "INACTIVE_MEMBER", "사용할 수 없는 회원입니다.",
            "탈퇴했거나 더 이상 사용할 수 없는 회원입니다. 다시 로그인하세요."),
    // 애플 회원은 애플 토큰을 철회해야 해서 일반 탈퇴로 탈퇴할 수 없다. (ADR 0025)
    APPLE_REAUTHENTICATION_REQUIRED(ErrorKind.INVALID_INPUT, "APPLE_REAUTHENTICATION_REQUIRED",
            "애플 재인증이 필요합니다.",
            "애플 회원은 애플 로그인을 다시 한 뒤 POST /api/v1/auth/apple/withdrawal로 탈퇴하세요."),
    INVALID_NICKNAME(ErrorKind.INVALID_INPUT, "VALIDATION_ERROR", "요청 값이 올바르지 않습니다.",
            "닉네임은 제어 문자를 제외한 1자 이상 20자 이하여야 합니다.");

    private final ErrorKind kind;
    private final String code;
    private final String title;
    private final String detail;

    MemberErrorCode(ErrorKind kind, String code, String title, String detail) {
        this.kind = kind;
        this.code = code;
        this.title = title;
        this.detail = detail;
    }

    @Override
    public ErrorKind kind() {
        return kind;
    }

    @Override
    public String code() {
        return code;
    }

    @Override
    public String title() {
        return title;
    }

    @Override
    public String detail() {
        return detail;
    }
}
