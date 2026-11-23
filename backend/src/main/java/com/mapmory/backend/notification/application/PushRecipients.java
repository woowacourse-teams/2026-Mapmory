package com.mapmory.backend.notification.application;

import com.mapmory.backend.member.api.MemberQuery;
import com.mapmory.backend.notification.domain.NotificationSettingRepository;
import com.mapmory.backend.notification.domain.PushToken;
import com.mapmory.backend.notification.domain.PushTokenRepository;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 알림을 받을 기기를 고른다. 어떤 알림이든 기기는 여기서 찾는다.
 *
 * <p>탈퇴하거나 버려진 회원의 토큰은 하루 한 번 지우므로(InactivePushTokenCleanup) 그때까지 표에 남아 있다.
 * 활동 중인지를 알림마다 거르면 새 알림에서 빠뜨리기 쉬워, 기기를 찾는 창구를 이 클래스 하나로 둔다.
 * 끄기 전에 적힌 알림도 끈 뒤에는 가지 않도록, 적을 때가 아니라 보낼 때 고른다.
 */
@Component
public class PushRecipients {

    private final PushTokenRepository pushTokenRepository;
    private final NotificationSettingRepository notificationSettingRepository;
    private final MemberQuery memberQuery;

    public PushRecipients(PushTokenRepository pushTokenRepository,
                          NotificationSettingRepository notificationSettingRepository,
                          MemberQuery memberQuery) {
        this.pushTokenRepository = pushTokenRepository;
        this.notificationSettingRepository = notificationSettingRepository;
        this.memberQuery = memberQuery;
    }

    /**
     * 앨범 알림을 받을 기기. 활동 중이고 그룹 앨범 알림을 끄지 않은 멤버의 기기다.
     */
    public List<PushToken> albumDevicesOf(List<Long> memberIds) {
        List<Long> active = without(memberIds, memberQuery.inactiveAmong(memberIds));
        if (active.isEmpty()) {
            return List.of();
        }
        List<Long> recipients = without(active, notificationSettingRepository.findGroupAlbumOffAmong(active));
        if (recipients.isEmpty()) {
            return List.of();
        }
        return pushTokenRepository.findAllByMemberIdIn(recipients);
    }

    /**
     * 요청 때 정한 기기. 그 회원의 등록이고 회원이 활동 중일 때만 고른다. 없으면 empty다.
     */
    public Optional<PushToken> registeredDevice(Long memberId, Long pushTokenId) {
        if (!memberQuery.inactiveAmong(List.of(memberId)).isEmpty()) {
            return Optional.empty();
        }
        return pushTokenRepository.findByIdAndMemberId(pushTokenId, memberId);
    }

    private List<Long> without(Collection<Long> memberIds, Set<Long> excluded) {
        return memberIds.stream()
                .filter(memberId -> !excluded.contains(memberId))
                .toList();
    }
}
