package com.mapmory.backend.admin.dashboard;

import com.mapmory.backend.common.exception.ErrorCode;
import com.mapmory.backend.common.exception.ErrorKind;

public enum AdminDashboardErrorCode implements ErrorCode {
    INVALID_DATE_RANGE(
            ErrorKind.INVALID_INPUT,
            "INVALID_DASHBOARD_DATE_RANGE",
            "조회 기간이 올바르지 않습니다.",
            "from은 to보다 늦을 수 없습니다."
    );

    private final ErrorKind kind;
    private final String code;
    private final String title;
    private final String detail;

    AdminDashboardErrorCode(ErrorKind kind, String code, String title, String detail) {
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
