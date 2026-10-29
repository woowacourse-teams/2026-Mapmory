package com.mapmory.backend.notification.application;

import com.mapmory.backend.notification.application.port.PushMessage;
import java.util.Map;

/**
 * 앨범 알림이 어떻게 생겼는지. 제목은 그룹 이름, 본문은 올린 사람과 앨범, data는 눌렀을 때 갈 앨범이다.
 * 같은 알림을 받을 기기마다 {@link #to}로 나눠 보낸다.
 */
record AlbumPush(String title, String body, Map<String, String> data) {

    static final String DEFAULT_NICKNAME = "멤버";

    AlbumPush {
        data = Map.copyOf(data);
    }

    /**
     * @param nickname 올린 사람 닉네임. 못 찾았으면 null이고 {@link #DEFAULT_NICKNAME}으로 부른다
     */
    static AlbumPush of(AlbumPushPayload payload, String nickname) {
        String sender = nickname == null ? DEFAULT_NICKNAME : nickname;
        return new AlbumPush(
                payload.groupName(),
                "%s님이 '%s'에 사진을 올렸어요".formatted(sender, payload.albumName()),
                Map.of(
                        "type", "ALBUM_UPDATED",
                        "groupId", String.valueOf(payload.groupId()),
                        "albumId", String.valueOf(payload.albumId())
                )
        );
    }

    PushMessage to(String token) {
        return new PushMessage(token, title, body, data);
    }
}
