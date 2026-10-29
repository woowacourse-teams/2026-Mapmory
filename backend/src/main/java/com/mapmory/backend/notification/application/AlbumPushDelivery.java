package com.mapmory.backend.notification.application;

import com.mapmory.backend.group.api.AlbumUpdated;
import com.mapmory.backend.member.api.MemberQuery;
import com.mapmory.backend.notification.application.port.PushMessage;
import com.mapmory.backend.notification.application.port.PushSender;
import com.mapmory.backend.notification.domain.NotificationSettingRepository;
import com.mapmory.backend.notification.domain.PushToken;
import com.mapmory.backend.notification.domain.PushTokenRepository;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 아웃박스에서 꺼낸 앨범 알림을 받을 멤버의 기기에 보낸다(ADR 0022 결정 7).
 *
 * <p>실패하면 예외를 그대로 올린다. {@link PushOutboxRelay}가 받아 다시 보낼 차례를 정한다.
 * 그룹은 알림을 모르고 이벤트만 보낸다. 받을 멤버는 이벤트에 담겨 오므로 그룹을 읽지 않는다.
 */
@Component
public class AlbumPushDelivery {

    private final PushTokenRepository pushTokenRepository;
    private final PushTokenService pushTokenService;
    private final MemberQuery memberQuery;
    private final PushSender pushSender;
    private final NotificationSettingRepository notificationSettingRepository;

    public AlbumPushDelivery(PushTokenRepository pushTokenRepository, PushTokenService pushTokenService,
                             MemberQuery memberQuery, PushSender pushSender,
                             NotificationSettingRepository notificationSettingRepository) {
        this.pushTokenRepository = pushTokenRepository;
        this.pushTokenService = pushTokenService;
        this.memberQuery = memberQuery;
        this.pushSender = pushSender;
        this.notificationSettingRepository = notificationSettingRepository;
    }

    // 읽기만 하고 트랜잭션을 열지 않는다. 발송을 기다리는 동안 커넥션을 쥐지 않는다. 지우기는 PushTokenService가 한다.
    public void deliver(AlbumUpdated event) {
        List<Long> recipients = recipientsWithAlbumOn(event.recipientMemberIds());
        if (recipients.isEmpty()) {
            return;
        }
        List<PushToken> tokens = pushTokenRepository.findAllByMemberIdIn(recipients);
        if (tokens.isEmpty()) {
            return;
        }
        AlbumPush push = AlbumPush.of(event, memberQuery.nicknamesOf(Set.of(event.actorMemberId()))
                .get(event.actorMemberId()));
        List<PushMessage> messages = tokens.stream()
                .map(token -> push.to(token.getToken()))
                .toList();

        pushTokenService.removeInvalid(pushSender.send(messages));
    }

    // 보낼 때 거른다. 끄기 전에 적힌 알림도 끈 뒤에는 가지 않는다.
    private List<Long> recipientsWithAlbumOn(List<Long> memberIds) {
        if (memberIds.isEmpty()) {
            return memberIds;
        }
        Set<Long> off = notificationSettingRepository.findGroupAlbumOffAmong(memberIds);
        return memberIds.stream()
                .filter(memberId -> !off.contains(memberId))
                .toList();
    }
}
