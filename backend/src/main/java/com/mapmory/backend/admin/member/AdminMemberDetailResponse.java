package com.mapmory.backend.admin.member;

import com.mapmory.backend.member.AuthProvider;
import java.time.LocalDateTime;
import java.util.List;

public record AdminMemberDetailResponse(
        MemberSummary member,
        long totalTravelRecords,
        long totalPhotos,
        List<TravelRecordItem> travelRecords,
        int page,
        int size,
        long totalElements,
        int totalPages
) {

    public record MemberSummary(
            Long id,
            String displayName,
            AuthProvider provider,
            LocalDateTime joinedAt
    ) {
    }

    public record TravelRecordItem(
            Long id,
            LocalDateTime createdAt,
            long photoCount
    ) {
    }
}
