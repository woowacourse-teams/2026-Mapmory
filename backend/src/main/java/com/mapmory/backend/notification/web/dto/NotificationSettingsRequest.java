package com.mapmory.backend.notification.web.dto;

import jakarta.validation.constraints.NotNull;

/**
 * @param groupAlbum 그룹 앨범 알림을 받을지
 */
public record NotificationSettingsRequest(
        @NotNull Boolean groupAlbum
) {
}
