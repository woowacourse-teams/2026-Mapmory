package com.mapmory.backend.notification.web.dto;

import com.mapmory.backend.notification.domain.NotificationSetting;

public record NotificationSettingsResponse(boolean groupAlbum) {

    public static NotificationSettingsResponse from(NotificationSetting setting) {
        return new NotificationSettingsResponse(setting.isGroupAlbum());
    }
}
