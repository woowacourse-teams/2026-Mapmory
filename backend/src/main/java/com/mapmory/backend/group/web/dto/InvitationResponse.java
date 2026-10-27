package com.mapmory.backend.group.web.dto;

import com.mapmory.backend.group.domain.GroupInvitation;
import java.time.LocalDateTime;

public record InvitationResponse(String token, String code, LocalDateTime expiresAt) {

    public static InvitationResponse from(GroupInvitation invitation) {
        return new InvitationResponse(invitation.getToken(), invitation.getCode(), invitation.getExpiresAt());
    }
}
