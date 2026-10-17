package com.mapmory.backend.servicefeedback;

import com.mapmory.backend.common.exception.ErrorCode;
import com.mapmory.backend.common.exception.ErrorKind;

public enum ServiceFeedbackErrorCode implements ErrorCode {
    SERVICE_FEEDBACK_NOT_FOUND(
            ErrorKind.NOT_FOUND,
            "SERVICE_FEEDBACK_NOT_FOUND",
            "서비스 의견을 찾을 수 없습니다.",
            "요청한 서비스 의견이 존재하지 않습니다."
    ),
    INVALID_DATE_RANGE(
            ErrorKind.INVALID_INPUT,
            "INVALID_SERVICE_FEEDBACK_DATE_RANGE",
            "조회 기간이 올바르지 않습니다.",
            "from은 to보다 늦을 수 없습니다."
    );

    private final ErrorKind kind;
    private final String code;
    private final String title;
    private final String detail;

    ServiceFeedbackErrorCode(ErrorKind kind, String code, String title, String detail) {
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
