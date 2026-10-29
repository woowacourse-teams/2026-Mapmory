package com.mapmory.backend.notification.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PushTokenDeleteRequest(
        @NotBlank @Size(max = 255) String token
) {
}
