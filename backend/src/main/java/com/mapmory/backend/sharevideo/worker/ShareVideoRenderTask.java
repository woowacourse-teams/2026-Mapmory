package com.mapmory.backend.sharevideo.worker;

import com.mapmory.backend.sharevideo.ShareVideoStyle;

public record ShareVideoRenderTask(
        Long jobId,
        ShareVideoStyle styleId,
        String snapshotJson
) {
    public ShareVideoRenderTask(final Long jobId, final String snapshotJson) {
        this(jobId, ShareVideoStyle.defaultStyle(), snapshotJson);
    }
}
