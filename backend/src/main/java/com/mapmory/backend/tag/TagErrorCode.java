package com.mapmory.backend.tag;

import com.mapmory.backend.common.exception.ErrorCode;
import com.mapmory.backend.common.exception.ErrorKind;

public enum TagErrorCode implements ErrorCode {
    INVALID_TAG_NAME(ErrorKind.INVALID_INPUT, "VALIDATION_ERROR", "요청 값이 올바르지 않습니다.",
            "태그 이름은 #과 제어 문자를 제외한 1자 이상 30자 이하여야 합니다."),
    TAG_NOT_FOUND(ErrorKind.NOT_FOUND, "TAG_NOT_FOUND", "태그를 찾을 수 없습니다.",
            "요청한 태그가 없거나 접근할 권한이 없습니다."),
    TAG_NAME_CONFLICT(ErrorKind.CONFLICT, "TAG_NAME_CONFLICT", "같은 이름의 태그가 있습니다.",
            "현재 회원에게 같은 이름의 태그가 이미 있습니다."),
    TAG_LIMIT_EXCEEDED(ErrorKind.CONFLICT, "TAG_LIMIT_EXCEEDED", "태그 개수 제한을 초과했습니다.",
            "회원은 현재 최대 10개의 태그를 만들 수 있습니다."),
    TOO_MANY_TAGS(ErrorKind.INVALID_INPUT, "TOO_MANY_TAGS", "여행 일지의 태그 개수 제한을 초과했습니다.",
            "여행 일지에는 현재 최대 5개의 태그를 연결할 수 있습니다."),
    INVALID_TAG_IDS(ErrorKind.INVALID_INPUT, "VALIDATION_ERROR", "요청 값이 올바르지 않습니다.",
            "태그 ID는 중복될 수 없습니다.");

    private final ErrorKind kind;
    private final String code;
    private final String title;
    private final String detail;

    TagErrorCode(ErrorKind kind, String code, String title, String detail) {
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
