package com.mapmory.backend.tag;

/**
 * 회원의 태그가 삭제됐다. 이름만 바뀐 경우는 태그로 거른 결과가 그대로라 내지 않는다.
 */
public record TagDeletedEvent(Long memberId) {
}
