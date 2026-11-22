package com.mapmory.backend.common.monitoring.db;

import com.mapmory.backend.common.monitoring.SloClass;
import io.micrometer.common.KeyValue;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.server.observation.ServerRequestObservationContext;

/**
 * 요청마다 DB 사용량을 모으고, 기준을 넘은 요청을 SLOW_REQUEST 한 줄로 남긴다. ADR 0032.
 *
 * <p>{@code http.server.requests}와 SLO 카운터를 만드는 것과 같은 서버 요청 Observation을 받는다. 시작할 때 요청 스레드에
 * {@link RequestDbUsage}를 두고, 끝날 때 지운다. {@code uri}·{@code method}는 그 Observation이 정한 값이라 {@code uri}는
 * 원본 경로가 아니라 경로 패턴이다(초대 토큰이 남지 않는다).
 *
 * <p>요청 ID는 MDC(RequestIdFilter)로 로그에 붙는다. 서버 요청 Observation 필터가 RequestIdFilter 안쪽에서 돌기 때문이다.
 *
 * <p>Observation 핸들러의 예외는 요청을 처리하는 필터로 올라간다. 계측 때문에 요청이 실패하면 안 되므로 여기서 잡고 경고만 남긴다.
 * 끝날 때는 무엇이 실패하든 스레드의 {@link RequestDbUsage}부터 지운다.
 */
public class SlowRequestObservationHandler implements ObservationHandler<ServerRequestObservationContext> {

    private static final Logger log = LoggerFactory.getLogger(SlowRequestObservationHandler.class);

    private static final String USAGE = SlowRequestObservationHandler.class.getName() + ".usage";

    private final DbMonitoringProperties.SlowRequest slowRequest;
    private final LongSupplier nanoTime;

    public SlowRequestObservationHandler(DbMonitoringProperties.SlowRequest slowRequest, LongSupplier nanoTime) {
        this.slowRequest = slowRequest;
        this.nanoTime = nanoTime;
    }

    @Override
    public void onStart(ServerRequestObservationContext context) {
        try {
            context.put(USAGE, RequestDbUsage.start(context, nanoTime.getAsLong()));
        } catch (RuntimeException exception) {
            DbMonitoringFailure.warn(log, "onStart", exception);
        }
    }

    @Override
    public void onStop(ServerRequestObservationContext context) {
        RequestDbUsage usage = context.get(USAGE);
        if (usage == null) {
            return;
        }
        usage.finish();
        try {
            logIfSlow(context, usage);
        } catch (RuntimeException exception) {
            DbMonitoringFailure.warn(log, "onStop", exception);
        }
    }

    private void logIfSlow(ServerRequestObservationContext context, RequestDbUsage usage) {
        long latencyNanos = nanoTime.getAsLong() - usage.startNanos();
        String method = value(context, "method");
        String uri = value(context, "uri");
        SloClass sloClass = SloClass.of(method, uri);
        if (!slowRequest.isSlow(sloClass, latencyNanos, usage.queryCount())) {
            return;
        }
        log.atInfo()
                .addKeyValue("event", "SLOW_REQUEST")
                .addKeyValue("method", method)
                .addKeyValue("uri", uri)
                .addKeyValue("status", context.getResponse() == null ? 0 : context.getResponse().getStatus())
                .addKeyValue("sloClass", sloClass.tagValue())
                .addKeyValue("latencyMs", TimeUnit.NANOSECONDS.toMillis(latencyNanos))
                .addKeyValue("dbQueryCount", usage.queryCount())
                .addKeyValue("dbTimeMs", TimeUnit.NANOSECONDS.toMillis(usage.dbTimeNanos()))
                .log("요청이 기준보다 오래 걸렸거나 SQL을 많이 실행했다");
    }

    @Override
    public boolean supportsContext(Observation.Context context) {
        return context instanceof ServerRequestObservationContext;
    }

    private static String value(ServerRequestObservationContext context, String key) {
        KeyValue keyValue = context.getLowCardinalityKeyValue(key);
        return keyValue == null ? KeyValue.NONE_VALUE : keyValue.getValue();
    }
}
