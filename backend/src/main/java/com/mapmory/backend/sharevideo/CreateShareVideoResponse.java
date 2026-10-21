package com.mapmory.backend.sharevideo;

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
