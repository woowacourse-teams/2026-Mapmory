package com.mapmory.backend.place.infrastructure.google.geocoding;

import com.mapmory.backend.place.infrastructure.google.GoogleMonthlyBudget;
import com.mapmory.backend.place.infrastructure.google.GoogleRequestPace;

/** 사진 좌표 → 지역의 Google 한도. 분당 한도(서버 메모리)를 먼저 보고 월 한도(DB)를 쓴다. 장소 검색 한도와 따로 센다. */
public class GoogleGeocodingLimiter {

    private final GoogleRequestPace pace;
    private final GoogleMonthlyBudget budget;

    GoogleGeocodingLimiter(GoogleRequestPace pace, GoogleMonthlyBudget budget) {
        this.pace = pace;
        this.budget = budget;
    }

    public void reserve() {
        pace.acquire();
        budget.consume();
    }

    public void refund() {
        budget.refund();
    }
}
