package com.mapmory.backend.group.api;

import java.util.List;

/**
 * 그룹원이 앨범을 열거나 새 기록을 담았다. 다른 컨텍스트(알림)가 받는다(ADR 0022 결정 7).
 *
 * <p>앨범 쓰기와 같은 트랜잭션에서 나간다. 알림은 같은 트랜잭션에 아웃박스로 적어 두고 커밋된 뒤에 따로 보낸다.
 * 받을 멤버는 그룹 잠금 아래에서 정해 담는다. 올린 사람은 빠진다. 받는 쪽이 그룹을 읽지 않아도 된다.
 */
public record AlbumUpdated(
        Long groupId,
        String groupName,
        Long albumId,
        String albumName,
        Long actorMemberId,
        List<Long> recipientMemberIds
) {
    public AlbumUpdated {
        recipientMemberIds = List.copyOf(recipientMemberIds);
    }
}
