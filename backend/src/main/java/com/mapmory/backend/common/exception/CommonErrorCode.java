package com.mapmory.backend.common.exception;

import org.slf4j.event.Level;

/**
 * 도메인에 매이지 않은 오류 코드.
 */
public enum CommonErrorCode implements ErrorCode {
    /**
     * 같은 데이터를 바꾸는 요청이 겹쳐 DB 잠금 충돌(데드락, 잠금 대기 시간 초과, 이미 바뀐 행)로 처리하지 못했다.
     * 데이터는 롤백되어 그대로이고, 같은 요청을 다시 보내면 대개 성공한다. 서비스 장애가 아니라 WARN으로 남긴다.
     */
    RETRY_LATER(ErrorKind.SERVICE_UNAVAILABLE, "RETRY_LATER", "잠시 후 다시 시도해 주세요.",
            "같은 데이터를 바꾸는 요청이 겹쳐 처리하지 못했습니다. 잠시 후 다시 시도해 주세요.");

    private final ErrorKind kind;
    private final String code;
    private final String title;
    private final String detail;

    CommonErrorCode(ErrorKind kind, String code, String title, String detail) {
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

    @Override
    public Level logLevel() {
        return Level.WARN;
    }
}
