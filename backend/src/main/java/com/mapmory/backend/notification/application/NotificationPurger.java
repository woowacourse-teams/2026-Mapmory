package com.mapmory.backend.notification.application;

import com.mapmory.backend.member.api.MemberDataPurger;
import com.mapmory.backend.notification.domain.NotificationOutboxRepository;
import com.mapmory.backend.notification.domain.NotificationSettingRepository;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 회원의 알림 설정과 영상 알림 아웃박스를 지운다. 기기 토큰은 InactivePushTokenCleanup이 먼저 지운다.
 */
@Component
@Order(5)
class NotificationPurger implements MemberDataPurger {

    private final NotificationSettingRepository notificationSettingRepository;
    private final NotificationOutboxRepository notificationOutboxRepository;

    NotificationPurger(NotificationSettingRepository notificationSettingRepository,
                       NotificationOutboxRepository notificationOutboxRepository) {
        this.notificationSettingRepository = notificationSettingRepository;
        this.notificationOutboxRepository = notificationOutboxRepository;
    }

    @Override
    public void purge(Long memberId) {
        notificationSettingRepository.deleteAllByMemberId(memberId);
        notificationOutboxRepository.deleteAllByMemberId(memberId);
    }
}
