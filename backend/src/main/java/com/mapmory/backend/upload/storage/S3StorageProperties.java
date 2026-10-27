package com.mapmory.backend.upload.storage;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import org.hibernate.validator.constraints.time.DurationMax;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * S3 저장 위치와 호출 한도.
 *
 * keyPrefix는 공용 버킷에서 우리 객체를 한 곳에 모으기 위한 값으로, 반드시 있어야 한다.
 * 비어 있으면 객체가 공용 버킷 루트에 쌓여 다른 팀 경로와 섞이므로 기동을 실패시킨다.
 * 운영자가 콘솔의 경로 표기를 그대로 옮겨 적어 "/mapmory" 나 "mapmory/" 가 들어와도
 * 같은 위치를 가리키도록 앞뒤 공백과 슬래시를 떼어 낸 값을 담는다.
 *
 * signedUrlExpiration은 업로드·조회·다운로드에 내주는 서명 URL의 유효시간이다.
 * 상한 7일(604,800초)은 S3 SigV4 presigned URL이 허용하는 최대치다.
 */
@Validated
@ConfigurationProperties(prefix = "upload.storage.s3")
public record S3StorageProperties(
        @NotBlank String bucket,
        @NotBlank String region,
        String keyPrefix,

        @NotNull
        @DurationMin(seconds = 1)
        @DurationMax(seconds = 604_800)
        Duration signedUrlExpiration,

        @NotNull @Valid Client client
) {

    private static final String TRAVEL_RECORDS = "travel-records/";

    public S3StorageProperties {
        keyPrefix = normalize(keyPrefix);
        if (keyPrefix.isEmpty()) {
            throw new IllegalArgumentException("upload.storage.s3.key-prefix must not be blank");
        }
    }

    private static String normalize(String keyPrefix) {
        if (keyPrefix == null) {
            return "";
        }
        return keyPrefix.strip()
                .replaceAll("^/+", "")
                .replaceAll("/+$", "");
    }

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
     * 여행 기록 객체가 모이는 경로. 끝에 슬래시가 붙는다.
     */
    public String travelRecordsDirectory() {
        return keyPrefix + "/" + TRAVEL_RECORDS;
    }
}
