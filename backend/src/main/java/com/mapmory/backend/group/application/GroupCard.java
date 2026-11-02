package com.mapmory.backend.group.application;

import com.mapmory.backend.group.domain.GroupSummary;
import com.mapmory.backend.recordmedia.ExpiringUrl;

/**
 * 내 그룹 목록 한 줄과 그 대표 사진.
 *
 * @param coverPhoto 가장 최근에 기록이 담긴 앨범의 대표 사진. 앨범이나 사진이 없으면 null이다
 */
public record GroupCard(GroupSummary summary, ExpiringUrl coverPhoto) {
}
