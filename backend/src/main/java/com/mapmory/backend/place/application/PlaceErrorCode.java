package com.mapmory.backend.place.application;

import com.mapmory.backend.common.exception.ErrorCode;
import com.mapmory.backend.common.exception.ErrorKind;

public enum PlaceErrorCode implements ErrorCode {
    INVALID_PLACE_ID(ErrorKind.INVALID_INPUT, "INVALID_PLACE_ID", "장소 ID가 올바르지 않습니다.", "장소 ID를 확인해 주세요."),
    PLACE_NOT_FOUND(ErrorKind.NOT_FOUND, "PLACE_NOT_FOUND", "장소를 찾을 수 없습니다.", "선택한 장소가 더 이상 제공되지 않습니다."),
    PLACE_RATE_LIMITED(ErrorKind.TOO_MANY_REQUESTS, "PLACE_RATE_LIMITED", "장소 조회 한도를 초과했습니다.", "잠시 후 다시 시도해 주세요."),
    PLACE_SEARCH_BUDGET_EXHAUSTED(ErrorKind.TOO_MANY_REQUESTS, "PLACE_SEARCH_BUDGET_EXHAUSTED", "오늘 장소 검색 한도를 초과했습니다.", "일일 한도가 초기화된 후 다시 시도해 주세요."),
    PLACE_SELECTION_BUDGET_EXHAUSTED(ErrorKind.TOO_MANY_REQUESTS, "PLACE_SELECTION_BUDGET_EXHAUSTED", "오늘 장소 선택 한도를 초과했습니다.", "일일 한도가 초기화된 후 다시 시도해 주세요."),
    PLACE_PROVIDER_BUDGET_EXHAUSTED(ErrorKind.SERVICE_UNAVAILABLE, "PLACE_PROVIDER_BUDGET_EXHAUSTED", "오늘 장소 조회 한도가 소진되었습니다.", "일일 한도가 초기화된 후 다시 시도해 주세요."),
    PLACE_PROVIDER_UNAVAILABLE(ErrorKind.SERVICE_UNAVAILABLE, "PLACE_PROVIDER_UNAVAILABLE", "장소 검색을 사용할 수 없습니다.", "잠시 후 다시 시도해 주세요.");

    private final ErrorKind kind;
    private final String code;
    private final String title;
    private final String detail;

    PlaceErrorCode(ErrorKind kind, String code, String title, String detail) {
        this.kind = kind;
        this.code = code;
        this.title = title;
        this.detail = detail;
    }

    @Override public ErrorKind kind() { return kind; }
    @Override public String code() { return code; }
    @Override public String title() { return title; }
    @Override public String detail() { return detail; }
}
