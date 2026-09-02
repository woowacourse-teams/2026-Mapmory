package com.mapmory.backend.upload;

import java.net.URI;
import java.time.Duration;

/**
 * 파일 하나에 대해 발급한 presigned 업로드 정보.
 *
 * <p>URI와 Duration을 그대로 담는다. 문자열·초 단위 변환은 응답 DTO가 맡는다.
 */
public record PresignedUpload(
        String objectKey,
        URI presignedUrl,
        String method,
        String contentType,
        Duration expiration
) {
}
