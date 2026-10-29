package com.mapmory.backend.sharevideo.storage;

import java.nio.file.Path;

public interface ShareVideoStorage {

    String upload(Long jobId, Path videoPath);

    String attemptObjectKey(Long jobId, String attemptId);

    void uploadAttempt(String objectKey, Path videoPath);

    boolean exists(String objectKey);
}
