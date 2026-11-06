package com.mapmory.backend.notification.web.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record PushTokenDeleteRequest(
        @NotNull @Positive Long pushTokenId
) {
}
