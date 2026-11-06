package com.mapmory.backend.notification.application;

import com.mapmory.backend.group.api.AlbumUpdated;
import java.util.List;

/**
 * 아웃박스에 JSON으로 적는 앨범 알림 내용. 그룹 이벤트({@link AlbumUpdated})를 그대로 적지 않고 알림이 쓸 값만 옮긴다.
 *
 * <p>배포 전에 적힌 행을 새 코드가 읽는다. 필드는 더하기만 하고, 이름을 바꾸거나 빼지 않는다.
 * 모르는 필드는 읽을 때 무시하므로(Jackson 3 기본값) 되돌린 코드도 새 행을 읽는다.
 */
public record AlbumPushPayload(
        Long groupId,
        String groupName,
        Long albumId,
        String albumName,
        Long actorMemberId,
        List<Long> recipientMemberIds
) {
    public AlbumPushPayload {
        recipientMemberIds = List.copyOf(recipientMemberIds);
    }

    public static AlbumPushPayload from(AlbumUpdated event) {
        return new AlbumPushPayload(event.groupId(), event.groupName(), event.albumId(), event.albumName(),
                event.actorMemberId(), event.recipientMemberIds());
    }
}
