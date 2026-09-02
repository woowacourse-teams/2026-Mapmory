package com.mapmory.backend.upload;

/**
 * presigned 업로드 URL을 받으려는 파일 하나의 정보.
 * 웹 요청 스펙(UploadFileRequest)과 분리해 서비스가 API 계약에 의존하지 않게 한다.
 */
public record UploadFileCommand(
        String fileName,
        String contentType,
        long fileSize
) {
}
