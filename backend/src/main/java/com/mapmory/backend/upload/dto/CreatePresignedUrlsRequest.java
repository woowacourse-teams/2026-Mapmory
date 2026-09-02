package com.mapmory.backend.upload.dto;

import com.mapmory.backend.upload.UploadFileCommand;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record CreatePresignedUrlsRequest(
        @NotEmpty(message = "업로드할 파일은 1개 이상이어야 합니다.")
        List<@Valid UploadFileRequest> files
) {

    public List<UploadFileCommand> toCommands() {
        return files.stream()
                .map(UploadFileRequest::toCommand)
                .toList();
    }
}
