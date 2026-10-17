package com.mapmory.backend.servicefeedback;

import java.time.LocalDateTime;

public record ServiceFeedbackSubmitResponse(Long feedbackId, LocalDateTime createdAt) {

    public static ServiceFeedbackSubmitResponse from(ServiceFeedback feedback) {
        return new ServiceFeedbackSubmitResponse(feedback.getId(), feedback.getCreatedAt());
    }
}
