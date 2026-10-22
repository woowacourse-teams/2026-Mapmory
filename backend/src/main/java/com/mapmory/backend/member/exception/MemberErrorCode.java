package com.mapmory.backend.member.exception;

import com.mapmory.backend.common.exception.ErrorCode;
import com.mapmory.backend.common.exception.ErrorKind;

public enum MemberErrorCode implements ErrorCode {
    MEMBER_NOT_FOUND(ErrorKind.NOT_FOUND, "MEMBER_NOT_FOUND", "회원을 찾을 수 없습니다.",
            "요청한 회원이 존재하지 않습니다."),
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
