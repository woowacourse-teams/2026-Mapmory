package com.mapmory.backend.dropoff;

/** 장소 검색·선택이나 사진 업로드를 부른 회원과 앱. 앱 버전은 따로 받지 않아 User-Agent로 본다(iOS는 앱 버전이 들어 있다). */
public record DropOffContext(Long memberId, String userAgent) {

    /** 남기지 않는 호출(테스트 등). */
    public static final DropOffContext NONE = new DropOffContext(null, null);
}
