package com.mapmory.backend.upload.dto;

import com.mapmory.backend.upload.PresignedUpload;
import java.util.List;

public record CreatePresignedUrlsResponse(
        List<PresignedUploadResponse> uploads
) {

    public static CreatePresignedUrlsResponse from(List<PresignedUpload> uploads) {
        return new CreatePresignedUrlsResponse(
                uploads.stream()
                        .map(PresignedUploadResponse::from)
                        .toList()
        );
    }
}
