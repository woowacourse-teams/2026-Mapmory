package com.mapmory.backend.upload.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 회원별 업로드 URL 발급 한도. 요청 횟수가 아니라 파일 수로 센다.
 */
@ConfigurationProperties(prefix = "upload.rate-limit")
record UploadRateLimitProperties(
        int filesPerMemberPerDay
) {
    UploadRateLimitProperties {
        if (filesPerMemberPerDay < 1) {
            throw new IllegalArgumentException("업로드 회원 한도 설정이 올바르지 않습니다.");
        }
    }
}
