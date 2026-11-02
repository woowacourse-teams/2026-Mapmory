package com.mapmory.backend.notification.exception;

import com.mapmory.backend.common.exception.ErrorCode;
import com.mapmory.backend.common.exception.ErrorKind;

public enum NotificationErrorCode implements ErrorCode {

    PUSH_REGISTRATION_CONFLICT;

    @Override
    public ErrorKind kind() {
        return ErrorKind.CONFLICT;
    }

    @Override
    public String code() {
        return name();
    }

    @Override
    public String title() {
        return "푸시 등록 정보가 다른 요청과 충돌했습니다.";
    }

    @Override
    public String detail() {
        return "잠시 후 푸시 등록을 다시 요청해 주세요.";
    }
}
