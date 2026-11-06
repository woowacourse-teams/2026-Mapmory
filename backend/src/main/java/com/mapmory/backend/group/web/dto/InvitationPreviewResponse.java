package com.mapmory.backend.group.web.dto;

import com.mapmory.backend.group.application.InvitationPreview;
import java.time.LocalDateTime;

public record InvitationPreviewResponse(
        Long groupId,
        String groupName,
        int memberCount,
        boolean alreadyMember,
        boolean full,
        LocalDateTime expiresAt
) {

    public static InvitationPreviewResponse from(InvitationPreview preview) {
        return new InvitationPreviewResponse(preview.groupId(), preview.groupName(), preview.memberCount(),
                preview.alreadyMember(), preview.full(), preview.expiresAt());
    }
}
