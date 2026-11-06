package com.mapmory.backend.travelrecord.api;

/**
 * 여행 기록이 지워졌다. 다른 컨텍스트가 이 기록을 가리키던 값을 정리할 때 받는다(그룹: 앨범 항목, ADR 0022 결정 5).
 *
 * <p>기록 삭제와 같은 트랜잭션에서 나간다. 받는 쪽은 커밋된 뒤에 처리해야 한다. 롤백된 삭제로 정리하면 안 된다.
 */
public record TravelRecordDeleted(Long travelRecordId, Long memberId) {
}
