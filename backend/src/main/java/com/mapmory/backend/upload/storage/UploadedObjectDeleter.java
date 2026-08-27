package com.mapmory.backend.upload.storage;

import java.util.List;

public interface UploadedObjectDeleter {

    void deleteAll(List<String> objectKeys);
}
