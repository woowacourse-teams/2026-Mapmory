package com.mapmory.backend.notification.web.dto;

import com.mapmory.backend.notification.domain.PushToken;

public record PushTokenResponse(
        Long pushTokenId
) {

    public static PushTokenResponse from(PushToken token) {
        return new PushTokenResponse(token.getId());
    }
}
