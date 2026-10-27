package com.mapmory.backend.tag;

/**
 * 회원의 태그 이름이 바뀌었거나 태그가 삭제됐다. 일지 목록은 태그 이름을 보여 주고,
 * 지도 요약과 일지 목록은 태그로 거를 수 있어서 둘 다 그 회원 캐시를 비운다.
 */
public record TagChangedEvent(Long memberId) {
}
