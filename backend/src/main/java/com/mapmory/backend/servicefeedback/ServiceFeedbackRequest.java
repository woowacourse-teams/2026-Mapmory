package com.mapmory.backend.servicefeedback;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ServiceFeedbackRequest(
        @NotNull ServiceFeedbackCategory category,
        @NotBlank @Size(max = 2000) String content,
        @NotNull ServiceFeedbackPlatform platform
) {

    public ServiceFeedbackRequest {
        content = content == null ? null : content.trim();
    }
}
