package com.mapmory.backend.travelrecord.thumbnail;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.nio.file.Path;
import java.time.Duration;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 렌더러 서버에서 도는 썸네일 워커의 설정. API 서버에서는 enabled를 끈다.
 *
 * @param batchSize       한 차례에 읽는 사진 수
 * @param maxEdge         썸네일의 긴 변(px)
 * @param processTimeout  사진 한 장의 변환 한도
 * @param maxFailures     같은 사진을 이 횟수만큼 실패하면 프로세스가 다시 뜰 때까지 건너뛴다
 * @param transferTimeout 원본 내려받기와 썸네일 올리기 한 번의 한도. 사진 확인용 기본 한도(1초)로는 큰 원본을 받지 못한다
 */
@Validated
@ConfigurationProperties(prefix = "media.thumbnail.worker")
public record ThumbnailWorkerProperties(
        boolean enabled,
        @NotNull @DurationMin(seconds = 1) Duration pollDelay,
        @Positive int batchSize,
        @Positive int maxEdge,
        @NotNull @DurationMin(millis = 1) Duration processTimeout,
        @Positive int maxFailures,
        @NotNull Path scriptDirectory,
        @NotBlank String pythonCommand,
        @NotNull Path workDirectory,
        @NotNull @DurationMin(seconds = 1) Duration transferTimeout
) {
}
