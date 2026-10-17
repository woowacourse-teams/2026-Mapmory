package com.mapmory.backend.servicefeedback;

import java.util.List;

public record AdminServiceFeedbackListResponse(
        List<ServiceFeedbackResponse> feedback,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}
