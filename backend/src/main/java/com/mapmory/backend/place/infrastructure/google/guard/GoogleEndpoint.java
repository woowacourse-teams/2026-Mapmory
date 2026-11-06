package com.mapmory.backend.place.infrastructure.google.guard;

import com.mapmory.backend.common.http.ExternalCallFailure;

/**
 * 우리가 부르는 Google 엔드포인트(Autocomplete, Place Details, Reverse Geocode) 하나의 명세다. 어느 호스트(서킷 브레이커)로 가는지, 어떤 실패가 과금되지 않는지,
 * 어떤 실패가 "대상 없음"인지만 담는다. 월 한도는 {@link GoogleRequestLimiter}가 엔드포인트별로 센다.
 * 호출 순서는 {@link GoogleCallGuard}, 실패를 우리 에러로 바꾸는 일은 {@link GoogleFailureTranslator}가 맡는다.
 */
public enum GoogleEndpoint {

    AUTOCOMPLETE(Host.PLACES, false),
    DETAILS(Host.PLACES, false) {
        /** 장소 ID가 없거나 잘못되면 404·400이다. 잘못된 키도 400으로 오므로 본문에 API_KEY가 있으면 제외한다. */
        @Override
        boolean notFound(ExternalCallFailure failure) {
            return failure.status() == 404
                    || failure.status() == 400 && !failure.responseBody().contains("API_KEY");
        }
    },
    // Geocoding은 성공한 요청만 과금하므로 4xx도 돌려준다.
    GEOCODE(Host.GEOCODING, true);

    final Host host;
    private final boolean rejectedNotBilled;

    GoogleEndpoint(Host host, boolean rejectedNotBilled) {
        this.host = host;
        this.rejectedNotBilled = rejectedNotBilled;
    }

    /**
     * Google이 과금하지 않은 실패인지 본다. 5xx는 Google이 처리하지 못한 것이고, 나가지 않은 요청은 Google에 닿지 않았다.
     * 쿼터 초과(429)도 Google이 처리하지 않아 과금되지 않는다.
     * 답을 기다리다 끊긴 요청은 Google이 처리했을 수 있어 과금된 것으로 본다.
     */
    boolean notBilled(ExternalCallFailure failure) {
        return failure.serverError() || !failure.sent() || failure.status() == 429
                || rejectedNotBilled && failure.rejected();
    }

    /** 요청한 대상이 없다는 4xx인지 본다. 장애가 아니라 PLACE_NOT_FOUND로 답한다. */
    boolean notFound(ExternalCallFailure failure) {
        return false;
    }

    /** 호스트마다 서킷 브레이커와 로그 이벤트 이름을 따로 쓴다. 한쪽 장애가 다른 쪽을 막지 않는다. */
    enum Host {
        PLACES("googlePlaces", "GOOGLE_PLACES", true),
        // 요청에 사진 좌표가 담기므로 거절 사유(응답 본문)도 로그에 남기지 않는다.
        GEOCODING("googleGeocoding", "GOOGLE_GEOCODING", false);

        final String breakerName;
        final String eventPrefix;
        /** 거절(4xx)됐을 때 Google이 준 사유(응답 본문)를 로그에 남겨도 되는지. */
        final boolean logsRejectionReason;

        Host(String breakerName, String eventPrefix, boolean logsRejectionReason) {
            this.breakerName = breakerName;
            this.eventPrefix = eventPrefix;
            this.logsRejectionReason = logsRejectionReason;
        }
    }
}
