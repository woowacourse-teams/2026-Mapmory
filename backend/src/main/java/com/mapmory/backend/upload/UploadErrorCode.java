package com.mapmory.backend.upload;

import com.mapmory.backend.common.exception.ErrorCode;
import com.mapmory.backend.common.exception.ErrorKind;

public enum UploadErrorCode implements ErrorCode {

    INVALID_FILE_TYPE(
            ErrorKind.INVALID_INPUT,
            "허용되지 않은 파일 형식입니다.",
            "jpeg, png, webp, heic 형식의 이미지만 업로드할 수 있습니다."
    ),
    FILE_SIZE_EXCEEDED(
            ErrorKind.INVALID_INPUT,
            "파일 크기가 너무 큽니다.",
            "파일 크기가 업로드 가능한 최대 크기를 초과했습니다."
    ),
    TOO_MANY_FILES(
            ErrorKind.INVALID_INPUT,
            "파일 개수가 너무 많습니다.",
            "한 번에 업로드할 수 있는 최대 파일 개수를 초과했습니다."
    ),
    MEDIA_NOT_UPLOADED(
            ErrorKind.INVALID_INPUT,
            "업로드되지 않은 이미지입니다.",
            "첨부한 이미지 중 업로드가 완료되지 않은 항목이 있습니다. 다시 업로드한 뒤 저장해 주세요."
    ),
    STORAGE_UNAVAILABLE(
            ErrorKind.SERVICE_UNAVAILABLE,
            "이미지 저장소를 확인할 수 없습니다.",
            "이미지 저장소가 응답하지 않아 처리하지 못했습니다. 잠시 후 다시 시도해 주세요."
    );

    private final ErrorKind kind;
    private final String title;
    private final String detail;

    UploadErrorCode(ErrorKind kind, String title, String detail) {
        this.kind = kind;
        this.title = title;
        this.detail = detail;
    }

    @Override
    public ErrorKind kind() {
        return kind;
    }

    @Override
    public String code() {
        return name();
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
