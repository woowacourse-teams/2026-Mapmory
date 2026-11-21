package com.mapmory.backend.common.monitoring;

import io.micrometer.common.KeyValue;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import java.time.Duration;
import java.util.List;
import org.springframework.http.server.observation.ServerRequestObservationContext;

/**
 * HTTP 요청이 기준 시간 안에 끝났는지 센다. SLO(ADR 0031)의 "기준 시간 안에 끝난 요청의 비율"을 내는 지표다.
 *
 * <p>{@code http.server.requests}에 SLO 버킷을 붙이면 Prometheus registry가 그 지표를 summary에서 histogram으로 바꾸고
 * p50·p95·p99 quantile을 내보내지 않는다. 대시보드가 그 quantile을 쓰므로, 기존 지표는 그대로 두고 이 카운터를 따로 둔다.
 *
 * <p>{@code http.server.requests}를 만드는 것과 같은 서버 요청 Observation을 받아, 같은 요청을 같은 시점(요청이 끝날 때)에 센다.
 * {@code uri}·{@code method}·{@code outcome}은 그 Observation이 정한 값을 그대로 쓴다. 그래서 {@code uri}는 원본 경로가 아니라
 * 경로 패턴이다(예: {@code /api/v1/invitations/{tokenOrCode}}).
 *
 * <p>{@code le}는 Prometheus 히스토그램과 같은 뜻의 누적값이다. 요청 시간이 기준 이하인 모든 {@code le}에 1을 더하고,
 * {@code +Inf}는 전체 건수다. 예를 들어 0.5초 요청은 {@code le="1"}, {@code le="2"}, {@code le="+Inf"}에 하나씩 더한다.
 * 시계열이 곱해지지 않도록 {@code status}·{@code exception}은 태그로 두지 않는다.
 */
public class HttpServerSloObservationHandler implements ObservationHandler<ServerRequestObservationContext> {

    static final String METRIC_NAME = "http.server.requests.slo";
    static final List<Threshold> THRESHOLDS = List.of(
            new Threshold("0.3", Duration.ofMillis(300)),
            new Threshold("1", Duration.ofSeconds(1)),
            new Threshold("2", Duration.ofSeconds(2)));
    static final String INFINITY = "+Inf";

    private static final String START_NANOS = HttpServerSloObservationHandler.class.getName() + ".startNanos";
    private static final List<String> TAG_KEYS = List.of("uri", "method", "outcome");

    private final MeterRegistry meterRegistry;

    public HttpServerSloObservationHandler(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Override
    public void onStart(ServerRequestObservationContext context) {
        context.put(START_NANOS, meterRegistry.config().clock().monotonicTime());
    }

    @Override
    public void onStop(ServerRequestObservationContext context) {
        Long startNanos = context.get(START_NANOS);
        if (startNanos == null) {
            return;
        }
        long elapsedNanos = meterRegistry.config().clock().monotonicTime() - startNanos;
        String[] tags = tags(context);
        for (Threshold threshold : THRESHOLDS) {
            if (elapsedNanos <= threshold.limit().toNanos()) {
                counter(tags, threshold.le()).increment();
            }
        }
        counter(tags, INFINITY).increment();
    }

    @Override
    public boolean supportsContext(Observation.Context context) {
        return context instanceof ServerRequestObservationContext;
    }

    private String[] tags(ServerRequestObservationContext context) {
        String[] tags = new String[TAG_KEYS.size() * 2];
        for (int index = 0; index < TAG_KEYS.size(); index++) {
            String key = TAG_KEYS.get(index);
            KeyValue keyValue = context.getLowCardinalityKeyValue(key);
            tags[index * 2] = key;
            tags[index * 2 + 1] = keyValue == null ? KeyValue.NONE_VALUE : keyValue.getValue();
        }
        return tags;
    }

    private Counter counter(String[] tags, String le) {
        return Counter.builder(METRIC_NAME)
                .tags(tags)
                .tag("le", le)
                .register(meterRegistry);
    }

    record Threshold(String le, Duration limit) {
    }
}
