package com.mapmory.backend.place.application.port;

/** 회원의 장소 검색과 후보 선택 횟수를 제한한다. */
public interface PlaceRateLimitPort {

    void checkSearch(Long memberId);

    void checkSelection(Long memberId);
}
