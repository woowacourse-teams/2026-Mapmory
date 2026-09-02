package com.mapmory.backend.upload.dto;

import com.mapmory.backend.upload.PresignedUpload;

public record PresignedUploadResponse(
        String objectKey,
        String presignedUrl,
        String method,
        String contentType,
        long expiresIn
) {

    public static PresignedUploadResponse from(PresignedUpload upload) {
        return new PresignedUploadResponse(
                upload.objectKey(),
                upload.presignedUrl().toString(),
                upload.method(),
                upload.contentType(),
                upload.expiration().toSeconds()
        );
    }
}
