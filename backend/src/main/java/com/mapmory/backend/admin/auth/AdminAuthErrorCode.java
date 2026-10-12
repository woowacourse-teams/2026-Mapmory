package com.mapmory.backend.admin.auth;

import com.mapmory.backend.common.exception.ErrorCode;
import com.mapmory.backend.common.exception.ErrorKind;

public enum AdminAuthErrorCode implements ErrorCode {
    ADMIN_LOGIN_FAILED(
            ErrorKind.AUTHENTICATION_REQUIRED,
            "ADMIN_LOGIN_FAILED",
            "관리자 로그인에 실패했습니다.",
            "아이디 또는 비밀번호를 확인하세요."
    ),
    ADMIN_SESSION_REQUIRED(
            ErrorKind.AUTHENTICATION_REQUIRED,
            "ADMIN_SESSION_REQUIRED",
            "관리자 로그인이 필요합니다.",
            "관리자 계정으로 로그인한 뒤 다시 요청하세요."
    );

    private final ErrorKind kind;
    private final String code;
    private final String title;
    private final String detail;

    AdminAuthErrorCode(ErrorKind kind, String code, String title, String detail) {
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
