package com.mapmory.backend.sharevideo.dto;

import com.mapmory.backend.sharevideo.ShareVideoDownloadUrl;

public record ShareVideoDownloadResponse(
        Long jobId,
        String downloadUrl,
        long downloadUrlExpiresIn
) {

    public static ShareVideoDownloadResponse of(
            final Long jobId,
            final ShareVideoDownloadUrl downloadUrl
    ) {
        return new ShareVideoDownloadResponse(
                jobId,
                downloadUrl.url(),
                downloadUrl.expiresIn()
        );
    }
}
