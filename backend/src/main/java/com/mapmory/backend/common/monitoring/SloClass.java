package com.mapmory.backend.common.monitoring;

import java.time.Duration;
import java.util.Map;

/**
 * SLO 카운터의 {@code slo_class} 태그. ADR 0031 "SLI와 SLO" 표의 조회·저장·인증 지연 분류를 따른다.
 *
 * <p>분류는 경로 패턴과 HTTP 메서드로 정하고, 이 클래스의 {@link #ROUTES} 한 곳에서만 관리한다. 표에 없는 API는 {@link #OTHER}다.
 * SLO 대상 API를 더하거나 빼면 ADR 0031 표와 이 목록을 함께 고친다.
 */
public enum SloClass {
    /** 조회 지연(300ms): 지도 요약, 기록 목록·상세, Presigned URL. */
    READ("read", Duration.ofMillis(300)),
    /** 저장 지연(2초): 기록 생성·수정(v1, v2). */
    WRITE("write", Duration.ofSeconds(2)),
    /** 인증 지연(1초): 앱 로그인, 토큰 갱신. */
    AUTH("auth", Duration.ofSeconds(1)),
    /** SLO가 없다. 느린 요청 로그(SLOW_REQUEST)의 기준으로만 1초를 쓴다(ADR 0032). */
    OTHER("other", Duration.ofSeconds(1));

    static final Map<String, SloClass> ROUTES = Map.ofEntries(
            Map.entry("GET /api/v1/travel-records/map-summary", READ),
            Map.entry("GET /api/v1/travel-records/map-summary/regions/roots", READ),
            Map.entry("GET /api/v1/travel-records/map-summary/regions/{regionId}/children", READ),
            Map.entry("GET /api/v1/travel-records", READ),
            Map.entry("GET /api/v1/travel-records/{travelRecordId}", READ),
            Map.entry("POST /api/v1/uploads/presigned-urls", READ),

            Map.entry("POST /api/v1/travel-records", WRITE),
            Map.entry("PUT /api/v1/travel-records/{travelRecordId}", WRITE),
            Map.entry("POST /api/v2/travel-records", WRITE),
            Map.entry("PUT /api/v2/travel-records/{travelRecordId}", WRITE),

            Map.entry("POST /api/v1/auth/login/kakao", AUTH),
            Map.entry("POST /api/v1/auth/login/google", AUTH),
            Map.entry("POST /api/v1/auth/login/apple", AUTH),
            Map.entry("POST /api/v1/auth/login/guest", AUTH),
            Map.entry("POST /api/v1/auth/token/refresh", AUTH));

    private final String tagValue;
    private final Duration latencyObjective;

    SloClass(String tagValue, Duration latencyObjective) {
        this.tagValue = tagValue;
        this.latencyObjective = latencyObjective;
    }

    public static SloClass of(String method, String uriPattern) {
        return ROUTES.getOrDefault(method + " " + uriPattern, OTHER);
    }

    public String tagValue() {
        return tagValue;
    }

    /** ADR 0031의 지연 기준 시간. 이 시간 안에 끝나야 SLO를 지킨 요청이다. */
    public Duration latencyObjective() {
        return latencyObjective;
    }
}
