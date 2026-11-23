package com.mapmory.backend.common.monitoring.db;

import com.mapmory.backend.common.monitoring.SloClass;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 느린 요청(SLOW_REQUEST)과 느린 쿼리(SLOW_QUERY) 로그의 기준. ADR 0032.
 *
 * <p>로그를 남기는 기준이지 고쳐야 하는 기준(SLO)이 아니다. 측정할 때는 설정으로 0까지 낮춰 모든 요청·쿼리를 남긴다.
 *
 * @param slowRequest 요청 하나의 기준
 * @param slowQuery   SQL 하나의 기준
 */
@ConfigurationProperties(prefix = "monitoring")
public record DbMonitoringProperties(SlowRequest slowRequest, SlowQuery slowQuery) {

    public DbMonitoringProperties {
        if (slowRequest == null) {
            slowRequest = new SlowRequest(null, 0);
        }
        if (slowQuery == null) {
            slowQuery = new SlowQuery(null);
        }
    }

    /**
     * @param latencyThreshold    이 시간을 넘은 요청을 남긴다. 비우면 그 요청의 {@link SloClass} 기준(ADR 0031)을 쓰고,
     *                            0이면 모든 요청을 남긴다.
     * @param queryCountThreshold SQL 실행 수가 이 값을 넘은 요청도 남긴다. 0이면 이 기준은 쓰지 않는다.
     */
    public record SlowRequest(Duration latencyThreshold, int queryCountThreshold) {

        public SlowRequest {
            requireNotNegative(latencyThreshold, "monitoring.slow-request.latency-threshold");
            if (queryCountThreshold < 0) {
                throw new IllegalArgumentException("monitoring.slow-request.query-count-threshold must not be negative");
            }
        }

        boolean isSlow(SloClass sloClass, long latencyNanos, int queryCount) {
            Duration threshold = latencyThreshold == null ? sloClass.latencyObjective() : latencyThreshold;
            if (exceeds(latencyNanos, threshold)) {
                return true;
            }
            return queryCountThreshold > 0 && queryCount > queryCountThreshold;
        }
    }

    /**
     * @param threshold 이 시간을 넘은 SQL 하나를 남긴다. 비우면 100ms(조회 SLO 300ms의 1/3)다. 0이면 모든 SQL을 남긴다.
     */
    public record SlowQuery(Duration threshold) {

        static final Duration DEFAULT_THRESHOLD = Duration.ofMillis(100);

        public SlowQuery {
            requireNotNegative(threshold, "monitoring.slow-query.threshold");
            if (threshold == null) {
                threshold = DEFAULT_THRESHOLD;
            }
        }

        boolean isSlow(long elapsedNanos) {
            return exceeds(elapsedNanos, threshold);
        }
    }

    // 기준과 같은 시간은 기준 안이다(SLO의 "기준 시간 안에 끝난 요청"과 같다). 0이면 걸린 시간과 관계없이 모두 남긴다.
    private static boolean exceeds(long elapsedNanos, Duration threshold) {
        return threshold.isZero() || elapsedNanos > threshold.toNanos();
    }

    private static void requireNotNegative(Duration value, String name) {
        if (value != null && value.isNegative()) {
            throw new IllegalArgumentException(name + " must not be negative");
        }
    }
}
