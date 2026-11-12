package com.mapmory.backend.group.web.dto;

import com.mapmory.backend.group.domain.GroupInvitation;
import java.time.LocalDateTime;

/**
 * inviteUrl은 앱이 공유창에 그대로 넘기는 링크다. token은 링크 안에 이미 들어 있지만 기존 앱을 위해 남긴다.
 */
public record InvitationResponse(String token, String code, String inviteUrl, LocalDateTime expiresAt) {

    public static InvitationResponse of(GroupInvitation invitation, String inviteUrl) {
        return new InvitationResponse(invitation.getToken(), invitation.getCode(), inviteUrl,
                invitation.getExpiresAt());
    }
}
