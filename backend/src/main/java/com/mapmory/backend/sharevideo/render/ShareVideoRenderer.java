package com.mapmory.backend.sharevideo.render;

import com.mapmory.backend.sharevideo.worker.ShareVideoRenderTask;

public interface ShareVideoRenderer {

    RenderedShareVideo render(ShareVideoRenderTask task);

    void cleanup(RenderedShareVideo renderedShareVideo);
}
