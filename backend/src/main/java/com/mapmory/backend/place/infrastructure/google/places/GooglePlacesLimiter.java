package com.mapmory.backend.place.infrastructure.google.places;

import com.mapmory.backend.place.infrastructure.google.GoogleMonthlyBudget;
import com.mapmory.backend.place.infrastructure.google.GoogleRequestPace;

/**
 * 장소 검색의 Google 한도. 분당 한도(자동완성·상세 합쳐서, 서버 메모리)를 먼저 보고, 엔드포인트별 월 한도(DB)를 쓴다.
 * 회원 한도를 거치지 않는 기록 저장 재조회도 여기서 센다. 회원별 한도는 {@code place.ratelimit}이 따로 센다.
 */
public class GooglePlacesLimiter {

    private final GoogleRequestPace pace;
    private final GoogleMonthlyBudget autocomplete;
    private final GoogleMonthlyBudget details;

    GooglePlacesLimiter(GoogleRequestPace pace, GoogleMonthlyBudget autocomplete, GoogleMonthlyBudget details) {
        this.pace = pace;
        this.autocomplete = autocomplete;
        this.details = details;
    }

    /** 분당 한도를 먼저 확인한다. 거절된 요청이 DB에 가지 않고 월 한도도 쓰지 않게 하기 위해서다. */
    public void reserve(GooglePlacesEndpoint endpoint) {
        pace.acquire();
        budget(endpoint).consume();
    }

    /** Google이 과금하지 않은 호출은 월 한도를 돌려준다. */
    public void refund(GooglePlacesEndpoint endpoint) {
        budget(endpoint).refund();
    }

    private GoogleMonthlyBudget budget(GooglePlacesEndpoint endpoint) {
        return switch (endpoint) {
            case AUTOCOMPLETE -> autocomplete;
            case DETAILS -> details;
        };
    }
}
