package com.mapmory.backend.common.monitoring.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

class DbMonitoringPropertiesTest {

    // application.yaml은 환경변수가 없으면 빈 값을 넣는다. 빈 값이면 요청 기준은 SLO 기준, 쿼리 기준은 100ms다.
    @Test
    void 환경변수가_비어_있으면_요청은_SLO_기준_쿼리는_100ms_쿼리_수_기준은_끈다() {
        DbMonitoringProperties properties = bind(Map.of(
                "monitoring.slow-request.latency-threshold", "",
                "monitoring.slow-request.query-count-threshold", "0",
                "monitoring.slow-query.threshold", ""));

        assertThat(properties.slowRequest().latencyThreshold()).isNull();
        assertThat(properties.slowRequest().queryCountThreshold()).isZero();
        assertThat(properties.slowQuery().threshold()).isEqualTo(Duration.ofMillis(100));
    }

    @Test
    void 측정할_때는_0ms로_낮출_수_있다() {
        DbMonitoringProperties properties = bind(Map.of(
                "monitoring.slow-request.latency-threshold", "0ms",
                "monitoring.slow-query.threshold", "0ms"));

        assertThat(properties.slowRequest().latencyThreshold()).isZero();
        assertThat(properties.slowQuery().threshold()).isZero();
    }

    @Test
    void 음수_기준은_기동할_때_막는다() {
        assertThatThrownBy(() -> new DbMonitoringProperties.SlowRequest(null, -1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DbMonitoringProperties.SlowQuery(Duration.ofMillis(-1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static DbMonitoringProperties bind(Map<String, String> values) {
        return new Binder(new MapConfigurationPropertySource(values))
                .bindOrCreate("monitoring", DbMonitoringProperties.class);
    }
}
