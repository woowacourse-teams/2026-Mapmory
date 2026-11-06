package com.mapmory.backend.place.infrastructure.google.places;

import com.mapmory.backend.common.http.ExternalCallFailure;

/** 장소 검색이 부르는 Google 엔드포인트. 월 한도는 엔드포인트(SKU)마다 따로 센다. */
public enum GooglePlacesEndpoint {

    AUTOCOMPLETE,
    DETAILS {
        /** 장소 ID가 없거나 잘못되면 404·400이다. 잘못된 키도 400으로 오므로 본문에 API_KEY가 있으면 제외한다. */
        @Override
        boolean notFound(ExternalCallFailure failure) {
            return failure.status() == 404
                    || failure.status() == 400 && !failure.responseBody().contains("API_KEY");
        }
    };

    /** 요청한 대상이 없다는 4xx인지 본다. 장애가 아니라 PLACE_NOT_FOUND로 답한다. */
    boolean notFound(ExternalCallFailure failure) {
        return false;
    }
}
