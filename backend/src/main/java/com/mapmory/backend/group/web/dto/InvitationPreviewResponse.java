package com.mapmory.backend.group.web.dto;

import com.mapmory.backend.group.application.InvitationPreview;

public record InvitationPreviewResponse(String groupName, int memberCount, boolean alreadyMember) {

    public static InvitationPreviewResponse from(InvitationPreview preview) {
        return new InvitationPreviewResponse(preview.groupName(), preview.memberCount(), preview.alreadyMember());
    }
}
