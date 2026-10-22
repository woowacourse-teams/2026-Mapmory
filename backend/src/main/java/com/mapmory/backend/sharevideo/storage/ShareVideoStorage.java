package com.mapmory.backend.sharevideo.storage;

import java.nio.file.Path;

public interface ShareVideoStorage {

    String upload(Long jobId, Path videoPath);
}
