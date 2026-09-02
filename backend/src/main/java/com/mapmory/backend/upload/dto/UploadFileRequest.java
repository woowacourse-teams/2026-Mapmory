package com.mapmory.backend.upload.dto;

import com.mapmory.backend.upload.UploadFileCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record UploadFileRequest(
        @NotBlank(message = "파일 이름은 필수입니다.")
        String fileName,

        @NotBlank(message = "Content-Type은 필수입니다.")
        String contentType,

        @NotNull(message = "파일 크기는 필수입니다.")
        @Positive(message = "파일 크기는 양수여야 합니다.")
        Long fileSize
) {

    public UploadFileCommand toCommand() {
        return new UploadFileCommand(fileName, contentType, fileSize);
    }
}
