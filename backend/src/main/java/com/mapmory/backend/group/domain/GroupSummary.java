package com.mapmory.backend.group.domain;

/**
 * 내 그룹 목록 한 줄. 그룹 카드에 쓰는 이름과 멤버 수, 앨범 수다.
 */
public record GroupSummary(Long id, String name, long memberCount, long albumCount) {
}
