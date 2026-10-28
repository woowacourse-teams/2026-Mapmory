package com.mapmory.backend.sharevideo;

import com.mapmory.backend.common.exception.ErrorCode;
import com.mapmory.backend.common.exception.ErrorKind;

public enum ShareVideoErrorCode implements ErrorCode {

    INSUFFICIENT_TIMELINE_STOPS(
            ErrorKind.INVALID_INPUT,
            "INSUFFICIENT_TIMELINE_STOPS",
            "영상으로 만들 수 있는 이동 경로가 부족합니다.",
            "영상에 표시할 국가 정류장이 2개 이상 필요합니다."
    ),

    SHARE_VIDEO_JOB_NOT_FOUND(
            ErrorKind.NOT_FOUND,
            "SHARE_VIDEO_JOB_NOT_FOUND",
            "영상 생성 작업을 찾을 수 없습니다.",
            "존재하지 않거나 접근할 수 없는 영상 생성 작업입니다."
    ),

    SHARE_VIDEO_NOT_READY(
            ErrorKind.CONFLICT,
            "SHARE_VIDEO_NOT_READY",
            "영상이 아직 준비되지 않았습니다.",
            "영상 생성이 완료된 후 다시 요청해 주세요."
    ),

    SHARE_VIDEO_GENERATION_FAILED(
            ErrorKind.CONFLICT,
            "SHARE_VIDEO_GENERATION_FAILED",
            "영상 생성에 실패했습니다.",
            "영상을 다시 생성해 주세요."
    );

    private final ErrorKind kind;
    private final String code;
    private final String title;
    private final String detail;

    ShareVideoErrorCode(ErrorKind kind, String code, String title, String detail) {
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
