package com.mapmory.backend.sharevideo.dto;

import com.mapmory.backend.sharevideo.ShareVideoJob;
import com.mapmory.backend.sharevideo.ShareVideoStatus;

public record ShareVideoJobResponse(
        Long jobId,
        ShareVideoStatus status
) {

    public static ShareVideoJobResponse from(final ShareVideoJob shareVideoJob) {
        return new ShareVideoJobResponse(
                shareVideoJob.getId(),
                shareVideoJob.getStatus()
        );
    }
}
