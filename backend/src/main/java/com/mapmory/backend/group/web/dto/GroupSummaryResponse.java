package com.mapmory.backend.group.web.dto;

import com.mapmory.backend.group.domain.GroupSummary;

public record GroupSummaryResponse(Long id, String name, long memberCount, long albumCount) {

    public static GroupSummaryResponse from(GroupSummary summary) {
        return new GroupSummaryResponse(summary.id(), summary.name(), summary.memberCount(), summary.albumCount());
    }
}
