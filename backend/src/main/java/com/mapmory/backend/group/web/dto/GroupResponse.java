package com.mapmory.backend.group.web.dto;

import com.mapmory.backend.group.application.GroupDetail;
import java.time.LocalDateTime;
import java.util.List;

public record GroupResponse(Long id, String name, List<MemberResponse> members) {

    public static GroupResponse from(GroupDetail detail) {
        return new GroupResponse(
                detail.id(),
                detail.name(),
                detail.members().stream().map(MemberResponse::from).toList()
        );
    }

    /**
     * @param memberId 같은 닉네임을 구분하려고 준다. 앨범의 담은 사람과 같은 값이다
     */
    public record MemberResponse(Long memberId, String nickname, boolean owner, boolean me, LocalDateTime joinedAt) {

        static MemberResponse from(GroupDetail.MemberView member) {
            return new MemberResponse(member.memberId(), member.nickname(), member.owner(), member.me(),
                    member.joinedAt());
        }
    }
}
