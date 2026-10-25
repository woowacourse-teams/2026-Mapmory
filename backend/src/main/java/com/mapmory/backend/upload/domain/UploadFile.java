package com.mapmory.backend.upload.domain;

/**
 * UploadPolicy를 통과한 파일 하나. 이 값으로만 업로드 키와 URL을 만든다.
 *
 * fileType : Content-Type으로 정한 형식
 * size     : 바이트 단위 크기. presigned URL에 서명해 앱이 다른 크기로 올리지 못하게 한다
 */
public record UploadFile(
        UploadFileType fileType,
        long size
) {

    public String contentType() {
        return fileType.contentType();
    }
}
