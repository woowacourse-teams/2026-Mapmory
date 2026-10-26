package com.mapmory.backend.group.web.dto;

import com.mapmory.backend.group.application.GroupDetail;
import java.util.List;

public record GroupResponse(Long id, String name, List<MemberResponse> members) {

    public static GroupResponse from(GroupDetail detail) {
        return new GroupResponse(
                detail.id(),
                detail.name(),
                detail.members().stream().map(MemberResponse::from).toList()
        );
    }

    public record MemberResponse(String nickname, boolean owner, boolean me) {

        static MemberResponse from(GroupDetail.MemberView member) {
            return new MemberResponse(member.nickname(), member.owner(), member.me());
        }
    }
}
