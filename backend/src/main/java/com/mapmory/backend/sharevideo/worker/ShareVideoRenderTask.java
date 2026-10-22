package com.mapmory.backend.sharevideo.worker;

public record ShareVideoRenderTask(
        Long jobId,
        String snapshotJson
) {
}
