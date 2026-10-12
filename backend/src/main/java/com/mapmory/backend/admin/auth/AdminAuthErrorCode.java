package com.mapmory.backend.admin.auth;

import com.mapmory.backend.common.exception.ErrorCode;
import com.mapmory.backend.common.exception.ErrorKind;

public enum AdminAuthErrorCode implements ErrorCode {

    INVALID_CREDENTIALS (
            ErrorKind.AUTHENTICATION_REQUIRED,
            "ADMIN_INVALID_CREDENTIALS",
            "로그인에 실패했습니다.",
            "아이디 또는 비밀번호를 확인해 주세요."
    ),

    ADMIN_SESSION_REQUIRED (
            ErrorKind.AUTHENTICATION_REQUIRED,
            "ADMIN_SESSION_REQUIRED",
            "로그인이 필요합니다.",
            "관리자 로그인 후 다시 요청해 주세요."
    );

    private final ErrorKind errorKind;
    private final String code;
    private final String title;
    private final String detail;

    AdminAuthErrorCode (ErrorKind errorKind, String code, String title, String detail) {
        this.errorKind = errorKind;
        this.code = code;
        this.title = title;
        this.detail = detail;
    }

    @Override public ErrorKind kind() { return errorKind; }
    @Override public String code() { return code; }
    @Override public String title() { return title; }
    @Override public String detail() { return detail; }
}
