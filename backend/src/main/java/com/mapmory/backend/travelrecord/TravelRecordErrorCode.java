package com.mapmory.backend.travelrecord;

import com.mapmory.backend.common.exception.ErrorCode;
import com.mapmory.backend.common.exception.ErrorKind;

public enum TravelRecordErrorCode implements ErrorCode {

    REGION_REQUIRED(
            ErrorKind.INVALID_INPUT,
            "REGION_REQUIRED",
            "필수 지역 정보가 없습니다.",
            "여행 지역을 선택해 주세요."
    ),
    INVALID_PAGINATION(
            ErrorKind.INVALID_INPUT,
            "VALIDATION_ERROR",
            "요청 값이 올바르지 않습니다.",
            "페이지 번호 또는 페이지 크기가 올바르지 않습니다."
    ),
    INVALID_TRAVEL_DATE_RANGE(
            ErrorKind.INVALID_INPUT,
            "INVALID_TRAVEL_DATE_RANGE",
            "여행 날짜가 올바르지 않습니다.",
            "종료일은 시작일보다 빠를 수 없으며 미래 날짜를 입력할 수 없습니다."
    ),
    TRAVEL_RECORD_NOT_FOUND(
            ErrorKind.NOT_FOUND,
            "TRAVEL_RECORD_NOT_FOUND",
            "여행 일지를 찾을 수 없습니다.",
            "요청한 여행 일지가 없거나 조회할 권한이 없습니다."
    ),
    INVALID_OBJECT_KEY(
            ErrorKind.INVALID_INPUT,
            "INVALID_OBJECT_KEY",
            "Object Key가 올바르지 않습니다.",
            "중복되거나 다른 여행 일지에서 사용 중인 Object Key가 포함되어 있습니다."
    );

    private final ErrorKind kind;
    private final String code;
    private final String title;
    private final String detail;

    TravelRecordErrorCode(ErrorKind kind, String code, String title, String detail) {
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
