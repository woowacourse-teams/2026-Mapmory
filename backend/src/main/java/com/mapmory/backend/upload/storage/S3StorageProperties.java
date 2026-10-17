package com.mapmory.backend.upload.storage;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * S3 저장 위치와 호출 한도.
 *
 * keyPrefix는 공용 버킷에서 우리 객체를 한 곳에 모으기 위한 값이다.
 * 전용 버킷을 쓰는 환경에서는 비워 두면 된다.
 */
@Validated
@ConfigurationProperties(prefix = "upload.storage.s3")
public record S3StorageProperties(
        @NotBlank String bucket,
        @NotBlank String region,
        String keyPrefix,
        @NotNull @Valid Client client
) {

    /**
     * S3 호출을 기다리는 한도. S3가 멈춰도 요청 스레드를 오래 쥐지 않게 한다.
     *
     * attemptTimeout은 한 번 시도의 한도이고, totalTimeout은 재시도까지 합친 호출 하나의 한도다.
     */
    public record Client(
            @NotNull @DurationMin(millis = 1) Duration attemptTimeout,
            @NotNull @DurationMin(millis = 1) Duration totalTimeout,
            @Positive int maxAttempts
    ) {
    }

    /**
     * 앞뒤 슬래시를 제거한 prefix. 값이 없으면 빈 문자열이다.
     *
     * 운영자가 콘솔의 경로 표기를 그대로 옮겨 적어 "/mapmory" 나 "mapmory/" 가 들어와도
     * 같은 위치를 가리키게 한다.
     */
    public String normalizedKeyPrefix() {
        if (keyPrefix == null || keyPrefix.isBlank()) {
            return "";
        }
        return keyPrefix.strip()
                .replaceAll("^/+", "")
                .replaceAll("/+$", "");
    }
}
