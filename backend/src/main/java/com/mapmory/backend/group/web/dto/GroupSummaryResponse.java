package com.mapmory.backend.group.web.dto;

import com.mapmory.backend.group.application.GroupCard;
import com.mapmory.backend.group.domain.GroupSummary;

/**
 * @param coverPhoto 가장 최근에 기록이 담긴 앨범의 대표 사진. 없으면 null이다
 */
public record GroupSummaryResponse(Long id, String name, long memberCount, long albumCount,
                                   PhotoUrlResponse coverPhoto) {

    public static GroupSummaryResponse from(GroupCard card) {
        GroupSummary summary = card.summary();
        return new GroupSummaryResponse(summary.id(), summary.name(), summary.memberCount(), summary.albumCount(),
                PhotoUrlResponse.fromNullable(card.coverPhoto()));
    }
}
