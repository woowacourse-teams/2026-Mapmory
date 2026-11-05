package com.mapmory.backend.place.infrastructure.google;

import com.mapmory.backend.common.http.ExternalCallFailure;
import java.util.function.Consumer;

/**
 * 우리가 부르는 Google API 하나하나의 규칙이다. 어느 서킷 브레이커를 쓰는지, 어느 월 한도를 쓰고 돌려주는지,
 * 어떤 실패가 과금되지 않는지를 한 곳에 둔다. 호출 흐름은 {@link GoogleCallGuard}가 맡는다.
 */
enum GoogleApi {

    AUTOCOMPLETE(Host.PLACES, GoogleRequestLimiter::reserveAutocomplete, GoogleRequestLimiter::refundAutocomplete,
            false),
    DETAILS(Host.PLACES, GoogleRequestLimiter::reserveDetails, GoogleRequestLimiter::refundDetails, false) {
        /** 장소 ID가 없거나 잘못되면 404·400이다. 잘못된 키도 400으로 오므로 본문에 API_KEY가 있으면 제외한다. */
        @Override
        boolean notFound(ExternalCallFailure failure) {
            return failure.status() == 404
                    || failure.status() == 400 && !failure.responseBody().contains("API_KEY");
        }
    },
    // Geocoding은 성공한 요청만 과금하므로 4xx도 돌려준다.
    GEOCODE(Host.GEOCODING, GoogleRequestLimiter::reserveGeocode, GoogleRequestLimiter::refundGeocode, true);

    final Host host;
    final Consumer<GoogleRequestLimiter> reserve;
    final Consumer<GoogleRequestLimiter> refund;
    private final boolean rejectedNotBilled;

    GoogleApi(Host host, Consumer<GoogleRequestLimiter> reserve, Consumer<GoogleRequestLimiter> refund,
              boolean rejectedNotBilled) {
        this.host = host;
        this.reserve = reserve;
        this.refund = refund;
        this.rejectedNotBilled = rejectedNotBilled;
    }

    /**
     * Google이 과금하지 않은 실패인지 본다. 5xx는 Google이 처리하지 못한 것이고, 나가지 않은 요청은 Google에 닿지 않았다.
     * 답을 기다리다 끊긴 요청은 Google이 처리했을 수 있어 과금된 것으로 본다.
     */
    boolean notBilled(ExternalCallFailure failure) {
        return failure.serverError() || !failure.sent() || rejectedNotBilled && failure.rejected();
    }

    /** 요청한 대상이 없다는 4xx인지 본다. 장애가 아니라 PLACE_NOT_FOUND로 답한다. */
    boolean notFound(ExternalCallFailure failure) {
        return false;
    }

    /** 호스트마다 서킷 브레이커와 로그 이벤트 이름을 따로 쓴다. 한쪽 장애가 다른 쪽을 막지 않는다. */
    enum Host {
        PLACES("googlePlaces", "GOOGLE_PLACES"),
        GEOCODING("googleGeocoding", "GOOGLE_GEOCODING");

        final String breakerName;
        final String eventPrefix;

        Host(String breakerName, String eventPrefix) {
            this.breakerName = breakerName;
            this.eventPrefix = eventPrefix;
        }
    }
}
