package com.mapmory.backend.notification.application;

import com.mapmory.backend.group.api.AlbumUpdated;
import com.mapmory.backend.member.api.MemberQuery;
import com.mapmory.backend.notification.application.port.PushMessage;
import com.mapmory.backend.notification.application.port.PushSender;
import com.mapmory.backend.notification.domain.PushToken;
import com.mapmory.backend.notification.domain.PushTokenRepository;
import com.mapmory.backend.notification.infrastructure.NotificationConfig;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 그룹원이 앨범에 올리면 같은 그룹의 다른 멤버 기기에 알린다(ADR 0022 결정 7).
 *
 * <p>앨범 쓰기가 커밋된 뒤 요청 스레드 밖에서 보낸다. 여기서 실패해도 올리기는 이미 성공했으므로 예외를 올리지 않고 남긴다.
 * 그룹은 알림을 모르고 이벤트만 보낸다. 받을 멤버는 이벤트에 담겨 오므로 그룹을 읽지 않는다.
 */
@Component
public class AlbumPushListener {

    private static final Logger log = LoggerFactory.getLogger(AlbumPushListener.class);

    private final PushTokenRepository pushTokenRepository;
    private final PushTokenService pushTokenService;
    private final MemberQuery memberQuery;
    private final PushSender pushSender;

    public AlbumPushListener(PushTokenRepository pushTokenRepository, PushTokenService pushTokenService,
                             MemberQuery memberQuery, PushSender pushSender) {
        this.pushTokenRepository = pushTokenRepository;
        this.pushTokenService = pushTokenService;
        this.memberQuery = memberQuery;
        this.pushSender = pushSender;
    }

    @Async(NotificationConfig.EXECUTOR)
    @TransactionalEventListener
    public void onAlbumUpdated(AlbumUpdated event) {
        try {
            notify(event);
        } catch (RuntimeException exception) {
            log.atWarn()
                    .addKeyValue("event", "ALBUM_PUSH_FAILED")
                    .addKeyValue("groupId", event.groupId())
                    .addKeyValue("albumId", event.albumId())
                    .setCause(exception)
                    .log("앨범 알림을 보내지 못했다");
        }
    }

    // 읽기만 하고 트랜잭션을 열지 않는다. 발송을 기다리는 동안 커넥션을 쥐지 않는다. 지우기는 PushTokenService가 한다.
    void notify(AlbumUpdated event) {
        if (event.recipientMemberIds().isEmpty()) {
            return;
        }
        List<PushToken> tokens = pushTokenRepository.findAllByMemberIdIn(event.recipientMemberIds());
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
}
