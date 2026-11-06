package com.mapmory.backend.place.infrastructure.google.places;

import com.mapmory.backend.common.http.ExternalCallFailure;
import com.mapmory.backend.place.exception.PlaceErrorCode;
import java.util.Locale;

/**
 * 장소 검색이 부르는 Google 엔드포인트. 월 한도는 엔드포인트(SKU)마다 따로 센다.
 * 엔드포인트를 더하면 여기 한 줄과 application.yaml의 google.places.rate-limit.per-month 한 줄만 더한다.
 */
public enum GooglePlacesEndpoint {

    AUTOCOMPLETE(PlaceErrorCode.PLACE_SEARCH_BUDGET_EXHAUSTED),
    DETAILS(PlaceErrorCode.PLACE_PROVIDER_BUDGET_EXHAUSTED) {
        /** 장소 ID가 없거나 잘못되면 404·400이다. 잘못된 키도 400으로 오므로 본문에 API_KEY가 있으면 제외한다. */
        @Override
        boolean notFound(ExternalCallFailure failure) {
            return failure.status() == 404
                    || failure.status() == 400 && !failure.responseBody().contains("API_KEY");
        }
    };

    private final PlaceErrorCode budgetExhausted;

    GooglePlacesEndpoint(PlaceErrorCode budgetExhausted) {
        this.budgetExhausted = budgetExhausted;
    }

    /** 월 한도의 이름. yaml 키(per-month.autocomplete)와 DB 키(google:autocomplete:2026-11)에 쓴다. */
    String budgetName() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** 이 엔드포인트의 월 한도를 다 썼을 때 답할 에러. */
    PlaceErrorCode budgetExhausted() {
        return budgetExhausted;
    }

    /** 요청한 대상이 없다는 4xx인지 본다. 장애가 아니라 PLACE_NOT_FOUND로 답한다. */
    boolean notFound(ExternalCallFailure failure) {
        return false;
    }
}
