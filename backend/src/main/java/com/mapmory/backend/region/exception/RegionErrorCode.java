package com.mapmory.backend.region.exception;

import com.mapmory.backend.common.exception.ErrorCode;
import com.mapmory.backend.common.exception.ErrorKind;

public enum RegionErrorCode implements ErrorCode {

    REGION_NOT_FOUND(
            ErrorKind.NOT_FOUND,
            "REGION_NOT_FOUND",
            "지역을 찾을 수 없습니다.",
            "요청한 지역 코드에 해당하는 지역이 없습니다."
    ),
    INVALID_REGION_HIERARCHY(
            ErrorKind.INVALID_INPUT,
            "INVALID_REGION_HIERARCHY",
            "지역 계층이 올바르지 않습니다.",
            "요청한 지역은 선택한 상위 지역에 속하지 않습니다."
    );

    private final ErrorKind kind;
    private final String code;
    private final String title;
    private final String detail;

    RegionErrorCode(ErrorKind kind, String code, String title, String detail) {
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
