package com.mapmory.backend.servicefeedback;

import jakarta.validation.constraints.NotNull;

public record AdminServiceFeedbackStatusRequest(@NotNull ServiceFeedbackStatus status) {
}
