package com.mapmory.backend.upload.config;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.convert.DataSizeUnit;
import org.springframework.util.unit.DataSize;
import org.springframework.util.unit.DataUnit;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "upload.policy")
public record UploadPolicyProperties(
        @NotNull
        @DataSizeUnit(DataUnit.BYTES)
        DataSize maxFileSize,

        @Positive int maxFilesPerRequest
) {
}
