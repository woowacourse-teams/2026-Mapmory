package com.mapmory.backend.common.monitoring;

import io.micrometer.common.KeyValue;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
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
 * <p>{@code le}는 Prometheus 히스토그램과 같은 뜻의 누적값이다. 요청 시간 이상인 모든 {@code le}(기준 안에 끝난 {@code le})에
 * 1을 더하고, {@code +Inf}는 전체 건수다. 예를 들어 0.5초 요청은 {@code le="1"}, {@code le="2"}, {@code le="+Inf"}에 하나씩 더한다.
 * 시계열이 곱해지지 않도록 {@code status}·{@code exception}은 태그로 두지 않는다.
 *
 * <p>{@code slo_class}는 ADR 0031의 조회·저장·인증 분류다({@link SloClass}). CloudWatch로는 {@code slo_class}·{@code outcome}·{@code le}만
 * 보내고, {@code uri}·{@code method}는 Prometheus에만 남긴다.
 */
public class HttpServerSloObservationHandler implements ObservationHandler<ServerRequestObservationContext> {

    static final String METRIC_NAME = "http.server.requests.slo";
    static final List<Threshold> THRESHOLDS = List.of(
            new Threshold("0.3", Duration.ofMillis(300)),
            new Threshold("1", Duration.ofSeconds(1)),
            new Threshold("2", Duration.ofSeconds(2)));
    static final String INFINITY = "+Inf";

    private static final String START_NANOS = HttpServerSloObservationHandler.class.getName() + ".startNanos";
    static final String SLO_CLASS_TAG = "slo_class";
    static final List<String> PRE_REGISTERED_OUTCOMES = List.of("SUCCESS", "CLIENT_ERROR", "SERVER_ERROR");

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
        // 기준을 넘은 요청도 모든 le 카운터를 만든다. CloudWatch Agent는 시계열마다 첫 값을 버리므로(delta로 바꿀 때),
        // le 시계열이 +Inf보다 늦게 생기면 그 첫 요청이 분자에서만 빠져 비율이 낮게 나온다. 같이 만들면 함께 빠진다.
        for (Threshold threshold : THRESHOLDS) {
            Counter counter = counter(tags, threshold.le());
            if (elapsedNanos <= threshold.limit().toNanos()) {
                counter.increment();
            }
        }
        counter(tags, INFINITY).increment();
    }

    @Override
    public boolean supportsContext(Observation.Context context) {
        return context instanceof ServerRequestObservationContext;
    }

    /**
     * SLO 대상 경로({@link SloClass#ROUTES})의 카운터를 0으로 미리 만든다.
     *
     * <p>CloudWatch Agent는 시계열마다 첫 수집값을 버린다. 첫 요청이 와서야 시계열이 생기면, 그 요청(주로 배포 직후라 가장 느린
     * 콜드 요청)이 분자·분모에서 함께 빠져 비율이 실제보다 좋게 나온다. 기동할 때 0으로 만들어 두면 버려지는 첫 값이 0이 되고,
     * 실제 첫 요청부터 CloudWatch에 잡힌다. Blue/Green에서 새 슬롯은 기동 뒤 헬스 체크를 거쳐 트래픽을 받으므로,
     * 대개 첫 수집(30초 간격)이 첫 요청보다 먼저 온다.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void registerSloRoutes() {
        for (String route : SloClass.ROUTES.keySet()) {
            String[] methodAndUri = route.split(" ", 2);
            for (String outcome : PRE_REGISTERED_OUTCOMES) {
                String[] tags = tags(methodAndUri[0], methodAndUri[1], outcome);
                for (Threshold threshold : THRESHOLDS) {
                    counter(tags, threshold.le());
                }
                counter(tags, INFINITY);
            }
        }
    }

    private String[] tags(ServerRequestObservationContext context) {
        return tags(value(context, "method"), value(context, "uri"), value(context, "outcome"));
    }

    private static String[] tags(String method, String uri, String outcome) {
        return new String[] {
                "uri", uri,
                "method", method,
                "outcome", outcome,
                SLO_CLASS_TAG, SloClass.of(method, uri).tagValue()};
    }

    private static String value(ServerRequestObservationContext context, String key) {
        KeyValue keyValue = context.getLowCardinalityKeyValue(key);
        return keyValue == null ? KeyValue.NONE_VALUE : keyValue.getValue();
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
