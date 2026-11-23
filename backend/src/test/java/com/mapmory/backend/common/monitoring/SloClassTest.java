package com.mapmory.backend.common.monitoring;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class SloClassTest {

    // ADR 0031 "SLI와 SLO" 표의 조회·저장·인증 지연 대상이 각 class로 가는지 본다.
    @ParameterizedTest(name = "{0} {1} → {2}")
    @CsvSource({
            // 조회 지연: 지도 요약, 기록 목록·상세, Presigned URL
            "GET, /api/v1/travel-records/map-summary/regions/roots, READ",
            "GET, /api/v1/travel-records/map-summary/regions/{regionId}/children, READ",
            "GET, /api/v1/travel-records/map-summary, READ",
            "GET, /api/v1/travel-records, READ",
            "GET, /api/v1/travel-records/{travelRecordId}, READ",
            "POST, /api/v1/uploads/presigned-urls, READ",
            // 저장 지연: 기록 생성·수정(v1, v2)
            "POST, /api/v1/travel-records, WRITE",
            "PUT, /api/v1/travel-records/{travelRecordId}, WRITE",
            "POST, /api/v2/travel-records, WRITE",
            "PUT, /api/v2/travel-records/{travelRecordId}, WRITE",
            // 인증 지연: 앱 로그인, 토큰 갱신
            "POST, /api/v1/auth/login/kakao, AUTH",
            "POST, /api/v1/auth/login/google, AUTH",
            "POST, /api/v1/auth/login/apple, AUTH",
            "POST, /api/v1/auth/login/guest, AUTH",
            "POST, /api/v1/auth/token/refresh, AUTH",
            // 표에 없는 API, 같은 경로의 다른 메서드
            "DELETE, /api/v1/travel-records/{travelRecordId}, OTHER",
            "GET, /api/v1/travel-records/statistics, OTHER",
            "POST, /api/v1/auth/logout, OTHER",
            "POST, /api/v1/admin/auth/login, OTHER",
            "GET, /api/v1/invitations/{tokenOrCode}, OTHER",
            "GET, /health, OTHER",
            "GET, UNKNOWN, OTHER",
    })
    void 경로_패턴과_메서드로_slo_class를_정한다(String method, String uriPattern, SloClass expected) {
        assertThat(SloClass.of(method, uriPattern)).isEqualTo(expected);
    }

    // 느린 요청 로그(ADR 0032)가 이 값을 기준으로 쓴다. ADR 0031 표와 같아야 한다.
    @Test
    void 지연_기준은_ADR_0031의_SLO와_같다() {
        assertThat(SloClass.READ.latencyObjective()).isEqualTo(Duration.ofMillis(300));
        assertThat(SloClass.WRITE.latencyObjective()).isEqualTo(Duration.ofSeconds(2));
        assertThat(SloClass.AUTH.latencyObjective()).isEqualTo(Duration.ofSeconds(1));
        assertThat(SloClass.OTHER.latencyObjective()).isEqualTo(Duration.ofSeconds(1));
    }
}
