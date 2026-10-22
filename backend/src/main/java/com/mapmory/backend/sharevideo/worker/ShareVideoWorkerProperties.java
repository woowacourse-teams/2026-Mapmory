package com.mapmory.backend.sharevideo.worker;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.nio.file.Path;
import java.time.Duration;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "share-video.worker")
public record ShareVideoWorkerProperties(
        boolean enabled,

        @NotNull
        @DurationMin(seconds = 1)
        Duration pollDelay,

        @NotNull
        Path rendererDirectory,

        @NotNull
        Path workDirectory,

        @NotNull
        @DurationMin(seconds = 1)
        Duration renderTimeout,

        @NotBlank
        String npmCommand
) {
}
