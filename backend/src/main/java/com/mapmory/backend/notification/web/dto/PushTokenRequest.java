package com.mapmory.backend.notification.web.dto;

import com.mapmory.backend.notification.domain.PushPlatform;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Positive;

/**
 * @param token    FCM 등록 토큰. push_token.token 컬럼 길이(255)까지다
 * @param platform ANDROID 또는 IOS
 */
public record PushTokenRequest(
        @NotBlank @Size(max = 255) String token,
        @NotNull PushPlatform platform,
        @Positive Long previousPushTokenId,
        Boolean photoRecordCompletionEnabled,
        Boolean travelVideoCompletionEnabled
) {
    public PushTokenRequest {
        photoRecordCompletionEnabled = photoRecordCompletionEnabled == null ? true : photoRecordCompletionEnabled;
        travelVideoCompletionEnabled = travelVideoCompletionEnabled == null ? true : travelVideoCompletionEnabled;
    }

    public PushTokenRequest(String token, PushPlatform platform) {
        this(token, platform, null, true, true);
    }
}
