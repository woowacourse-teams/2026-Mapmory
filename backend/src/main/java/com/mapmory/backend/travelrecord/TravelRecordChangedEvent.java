package com.mapmory.backend.travelrecord;

/**
 * 회원의 여행 일지가 생성·수정·삭제됐다. 일지로 계산한 값을 들고 있는 곳은 이 회원 것을 다시 계산해야 한다.
 */
public record TravelRecordChangedEvent(Long memberId) {
}
