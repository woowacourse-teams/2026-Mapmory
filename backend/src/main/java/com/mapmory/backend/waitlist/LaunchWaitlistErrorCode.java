package com.mapmory.backend.waitlist;

import com.mapmory.backend.common.exception.ErrorCode;
import com.mapmory.backend.common.exception.ErrorKind;

public enum LaunchWaitlistErrorCode implements ErrorCode {

    INVALID_EMAIL(
            ErrorKind.INVALID_INPUT,
            "VALIDATION_ERROR",
            "요청 값이 올바르지 않습니다.",
            "이메일은 비어 있을 수 없으며 254자 이하여야 합니다."
    );

    private final ErrorKind kind;
    private final String code;
    private final String title;
    private final String detail;

    LaunchWaitlistErrorCode(ErrorKind kind, String code, String title, String detail) {
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
