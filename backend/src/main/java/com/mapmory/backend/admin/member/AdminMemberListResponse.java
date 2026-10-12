package com.mapmory.backend.admin.member;

import com.mapmory.backend.member.AuthProvider;
import java.time.LocalDateTime;
import java.util.List;

public record AdminMemberListResponse(
        List<MemberItem> members,
        int page,
        int size,
        long totalElements,
        int totalPages
) {

    public record MemberItem(
            Long id,
            String displayName,
            AuthProvider provider,
            LocalDateTime joinedAt,
            long travelRecordCount
    ) {
    }
}
