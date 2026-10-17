package com.mapmory.backend.servicefeedback;

import java.time.LocalDateTime;

public record ServiceFeedbackResponse(
        Long feedbackId,
        ServiceFeedbackCategory category,
        String content,
        ServiceFeedbackPlatform platform,
        ServiceFeedbackStatus status,
        LocalDateTime createdAt
) {

    public static ServiceFeedbackResponse from(ServiceFeedback feedback) {
        return new ServiceFeedbackResponse(
                feedback.getId(),
                feedback.getCategory(),
                feedback.getContent(),
                feedback.getPlatform(),
                feedback.getStatus(),
                feedback.getCreatedAt()
        );
    }
}
