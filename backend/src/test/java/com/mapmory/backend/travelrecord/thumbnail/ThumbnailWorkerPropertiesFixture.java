package com.mapmory.backend.travelrecord.thumbnail;

import java.nio.file.Path;
import java.time.Duration;

final class ThumbnailWorkerPropertiesFixture {

    private ThumbnailWorkerPropertiesFixture() {
    }

    static ThumbnailWorkerProperties with(Path scriptDirectory, String pythonCommand, Duration processTimeout) {
        return new ThumbnailWorkerProperties(true, Duration.ofSeconds(10), 20, 1080, processTimeout, 3,
                scriptDirectory, pythonCommand, Path.of(System.getProperty("java.io.tmpdir"), "mapmory-thumbnail-test"),
                Duration.ofSeconds(30));
    }
}
