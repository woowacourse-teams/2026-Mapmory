package com.mapmory.backend.notification.application;

import com.mapmory.backend.member.application.ActiveMemberGuard;
import com.mapmory.backend.notification.domain.NotificationSetting;
import com.mapmory.backend.notification.domain.NotificationSettingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원별 알림 켜기·끄기. 기기마다가 아니라 회원마다라 다른 기기로 로그인해도 따라간다.
 * 기기 알림 권한(OS)은 서버가 알 수 없으므로 앱이 따로 안내한다.
 */
@Service
public class NotificationSettingService {

    private final NotificationSettingRepository notificationSettingRepository;
    private final ActiveMemberGuard activeMemberGuard;

    public NotificationSettingService(NotificationSettingRepository notificationSettingRepository,
                                      ActiveMemberGuard activeMemberGuard) {
        this.notificationSettingRepository = notificationSettingRepository;
        this.activeMemberGuard = activeMemberGuard;
    }

    /**
     * 한 번도 바꾸지 않았으면 켜짐이다.
     */
    @Transactional(readOnly = true)
    public boolean isGroupAlbumOn(Long memberId) {
        return notificationSettingRepository.findById(memberId)
                .map(NotificationSetting::isGroupAlbum)
                .orElse(true);
    }

    /**
     * 탈퇴 뒤 남은 access token으로는 바꾸지 못한다(#523).
     */
    @Transactional
    public void changeGroupAlbum(Long memberId, boolean on) {
        activeMemberGuard.require(memberId);
        notificationSettingRepository.upsertGroupAlbum(memberId, on);
    }
}
