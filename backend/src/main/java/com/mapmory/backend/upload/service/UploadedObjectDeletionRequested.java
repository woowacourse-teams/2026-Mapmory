package com.mapmory.backend.upload.service;

import java.util.List;

public record UploadedObjectDeletionRequested(List<String> objectKeys) {

    public UploadedObjectDeletionRequested {
        objectKeys = List.copyOf(objectKeys);
    }
}
