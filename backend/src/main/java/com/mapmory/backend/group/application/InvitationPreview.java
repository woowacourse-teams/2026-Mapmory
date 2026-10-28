package com.mapmory.backend.group.application;

import java.time.LocalDateTime;

/**
 * 수락 전에 보여 주는 초대. 어느 그룹인지와 내가 이미 멤버인지, 지금 들어갈 수 있는지를 담는다.
 *
 * @param groupId 이미 멤버면 앱이 바로 그룹으로 간다
 * @param full    인원이 다 찼다. 앱이 참여 버튼을 미리 막는다. 수락할 때 다시 판단하므로 그사이 바뀔 수 있다
 */
public record InvitationPreview(
        Long groupId,
        String groupName,
        int memberCount,
        boolean alreadyMember,
        boolean full,
        LocalDateTime expiresAt
) {
}
