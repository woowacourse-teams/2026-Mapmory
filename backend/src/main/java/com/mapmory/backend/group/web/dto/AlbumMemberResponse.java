package com.mapmory.backend.group.web.dto;

import com.mapmory.backend.group.application.AlbumDetail;

public record AlbumMemberResponse(Long memberId, String nickname, boolean me) {

    public static AlbumMemberResponse from(AlbumDetail.Contributor contributor) {
        return new AlbumMemberResponse(contributor.memberId(), contributor.nickname(), contributor.me());
    }
}
