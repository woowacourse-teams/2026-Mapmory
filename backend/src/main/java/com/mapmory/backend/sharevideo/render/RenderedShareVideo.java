package com.mapmory.backend.sharevideo.render;

import java.nio.file.Path;

public record RenderedShareVideo(
        Path videoPath,
        Path workDirectory
) {
}
