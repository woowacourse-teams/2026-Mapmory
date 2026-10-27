package com.mapmory.backend.place.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.slf4j.event.Level;

class PlaceErrorCodeTest {

    // 장애 하나에 알람이 한 번 울리도록 ERROR는 원인이 난 곳에서 남기고, 요청마다 남는 응답 로그는 WARN이다.
    @Test
    void 제공자_장애와_한도_소진은_요청마다_WARN으로_남긴다() {
        assertThat(PlaceErrorCode.PLACE_PROVIDER_UNAVAILABLE.logLevel()).isEqualTo(Level.WARN);
        assertThat(PlaceErrorCode.PLACE_PROVIDER_BUDGET_EXHAUSTED.logLevel()).isEqualTo(Level.WARN);
    }

    @Test
    void 나머지는_기본_규칙을_따른다() {
        assertThat(PlaceErrorCode.PLACE_NOT_FOUND.logLevel()).isEqualTo(Level.DEBUG);
        assertThat(PlaceErrorCode.PLACE_RATE_LIMITED.logLevel()).isEqualTo(Level.DEBUG);
    }
}
