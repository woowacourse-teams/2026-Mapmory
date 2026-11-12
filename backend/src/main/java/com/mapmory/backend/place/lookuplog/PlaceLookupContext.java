package com.mapmory.backend.place.lookuplog;

/** 장소 검색·선택을 부른 회원과 앱. 앱 버전은 따로 받지 않아 User-Agent로 본다(iOS는 앱 버전이 들어 있다). */
public record PlaceLookupContext(Long memberId, String userAgent) {

    /** 남기지 않는 호출(테스트 등). */
    public static final PlaceLookupContext NONE = new PlaceLookupContext(null, null);
}
