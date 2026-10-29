package com.mapmory.backend.notification.web;

import com.mapmory.backend.auth.security.LoginMember;
import com.mapmory.backend.common.dto.ApiResponse;
import com.mapmory.backend.member.Member;
import com.mapmory.backend.notification.application.NotificationSettingService;
import com.mapmory.backend.notification.web.dto.NotificationSettingsRequest;
import com.mapmory.backend.notification.web.dto.NotificationSettingsResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notification-settings")
public class NotificationSettingController {

    private final NotificationSettingService notificationSettingService;

    public NotificationSettingController(NotificationSettingService notificationSettingService) {
        this.notificationSettingService = notificationSettingService;
    }

    @GetMapping
    public ApiResponse<NotificationSettingsResponse> find(@LoginMember Member member) {
        return ApiResponse.from(new NotificationSettingsResponse(
                notificationSettingService.isGroupAlbumOn(member.getId())));
    }

    @PutMapping
    public ApiResponse<NotificationSettingsResponse> change(@LoginMember Member member,
                                                            @Valid @RequestBody NotificationSettingsRequest request) {
        notificationSettingService.changeGroupAlbum(member.getId(), request.groupAlbum());
        return ApiResponse.from(new NotificationSettingsResponse(request.groupAlbum()));
    }
}
