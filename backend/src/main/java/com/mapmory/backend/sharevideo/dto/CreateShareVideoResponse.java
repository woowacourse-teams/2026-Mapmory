package com.mapmory.backend.sharevideo.dto;

import com.mapmory.backend.sharevideo.ShareVideoJob;
import com.mapmory.backend.sharevideo.ShareVideoStatus;

public record CreateShareVideoResponse(
        Long jobId,
        ShareVideoStatus status
) {

    public static CreateShareVideoResponse from(
            final ShareVideoJob shareVideoJob
    ) {
        return new CreateShareVideoResponse(
                shareVideoJob.getId(),
                shareVideoJob.getStatus()
        );
    }
}
