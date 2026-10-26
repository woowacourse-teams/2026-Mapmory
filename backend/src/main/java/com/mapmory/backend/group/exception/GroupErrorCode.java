package com.mapmory.backend.group.exception;

import com.mapmory.backend.common.exception.ErrorCode;
import com.mapmory.backend.common.exception.ErrorKind;

public enum GroupErrorCode implements ErrorCode {
    // 멤버가 아닌 사람에게도 같은 응답을 준다. 그룹이 있는지 알려 주지 않기 위해서다.
    GROUP_NOT_FOUND(ErrorKind.NOT_FOUND, "GROUP_NOT_FOUND", "그룹을 찾을 수 없습니다.",
            "요청한 그룹이 없거나 멤버가 아닙니다."),
    // 게스트는 기기를 바꾸면 계정을 잃으므로 그룹을 쓰려면 소셜 로그인이 필요하다(ADR 0021 결정 6).
    SOCIAL_LOGIN_REQUIRED(ErrorKind.ACCESS_DENIED, "SOCIAL_LOGIN_REQUIRED", "소셜 로그인이 필요합니다.",
            "그룹은 소셜 로그인 회원만 쓸 수 있습니다."),
    INVALID_GROUP_NAME(ErrorKind.INVALID_INPUT, "VALIDATION_ERROR", "요청 값이 올바르지 않습니다.",
            "그룹 이름은 공백을 제외한 1자 이상 30자 이하여야 합니다."),
    INVALID_ALBUM_NAME(ErrorKind.INVALID_INPUT, "VALIDATION_ERROR", "요청 값이 올바르지 않습니다.",
            "앨범 이름은 공백을 제외한 1자 이상 200자 이하여야 합니다.");

    private final ErrorKind kind;
    private final String code;
    private final String title;
    private final String detail;

    GroupErrorCode(ErrorKind kind, String code, String title, String detail) {
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
