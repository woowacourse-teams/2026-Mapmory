package com.mapmory.backend.place.lookuplog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/**
 * 장소 검색·선택 한 번. 사용자가 어디서 막혀 기록을 남기지 못하고 나가는지 보고 재현하려고 30일만 둔다.
 *
 * <p>상황은 검색어·고른 장소 ID·앱 User-Agent, 문제는 outcome과 error_code, 결과는 후보 수와 찾은 국가·지역 코드다.
 * Google 약관상 장소는 ID만 남긴다. 같은 검색 세션은 session_token으로 잇는다.
 */
@Entity
@Table(name = "place_lookup_log")
public class PlaceLookupLog {

    static final int QUERY_LENGTH = 100;
    static final int USER_AGENT_LENGTH = 255;

    public enum Kind { SEARCH, SELECT }

    public enum Outcome {
        /** 검색 후보가 하나 이상 나왔다. */
        RESULTS,
        /** 검색 후보가 0개다. */
        NO_RESULTS,
        /** 장소의 지역을 찾았다. */
        REGION_FOUND,
        /** 국가는 알지만 지역을 못 찾아 사용자가 직접 골라야 한다. */
        REGION_NOT_FOUND,
        /** 국가도 지역도 몰라 사용자가 직접 골라야 한다. */
        COUNTRY_UNKNOWN,
        /** 요청이 오류로 끝났다. 코드는 error_code에 있다. */
        FAILED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Kind kind;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Outcome outcome;

    @Column(name = "session_token", length = 36)
    private String sessionToken;

    @Column(length = QUERY_LENGTH)
    private String query;

    @Column(name = "result_count")
    private Integer resultCount;

    @Column(name = "place_id", length = 255)
    private String placeId;

    @Column(name = "country_code", length = 10)
    private String countryCode;

    @Column(name = "region_code", length = 20)
    private String regionCode;

    @Column(name = "error_code", length = 50)
    private String errorCode;

    @Column(name = "user_agent", length = USER_AGENT_LENGTH)
    private String userAgent;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected PlaceLookupLog() {
    }

    PlaceLookupLog(PlaceLookupContext context, Kind kind, Outcome outcome, LocalDateTime createdAt) {
        this.memberId = context.memberId();
        this.userAgent = cut(context.userAgent(), USER_AGENT_LENGTH);
        this.kind = kind;
        this.outcome = outcome;
        this.createdAt = createdAt;
    }

    PlaceLookupLog sessionToken(String sessionToken) {
        this.sessionToken = sessionToken;
        return this;
    }

    PlaceLookupLog query(String query) {
        this.query = cut(query == null ? null : query.strip(), QUERY_LENGTH);
        return this;
    }

    PlaceLookupLog resultCount(Integer resultCount) {
        this.resultCount = resultCount;
        return this;
    }

    PlaceLookupLog place(String placeId, String countryCode, String regionCode) {
        this.placeId = cut(placeId, 255);
        this.countryCode = countryCode;
        this.regionCode = regionCode;
        return this;
    }

    PlaceLookupLog errorCode(String errorCode) {
        this.errorCode = errorCode;
        return this;
    }

    private static String cut(String value, int length) {
        if (value == null || value.length() <= length) {
            return value;
        }
        return value.substring(0, length);
    }

    public Long getMemberId() { return memberId; }
    public Kind getKind() { return kind; }
    public Outcome getOutcome() { return outcome; }
    public String getSessionToken() { return sessionToken; }
    public String getQuery() { return query; }
    public Integer getResultCount() { return resultCount; }
    public String getPlaceId() { return placeId; }
    public String getCountryCode() { return countryCode; }
    public String getRegionCode() { return regionCode; }
    public String getErrorCode() { return errorCode; }
    public String getUserAgent() { return userAgent; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
