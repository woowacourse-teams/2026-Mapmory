package com.mapmory.backend.upload.service;

import java.util.List;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
public class UploadedObjectDeletionScheduler {

    private final ApplicationEventPublisher eventPublisher;

    public UploadedObjectDeletionScheduler(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    public void schedule(List<String> objectKeys) {
        if (objectKeys.isEmpty()) {
            return;
        }
        eventPublisher.publishEvent(new UploadedObjectDeletionRequested(objectKeys));
    }
}
