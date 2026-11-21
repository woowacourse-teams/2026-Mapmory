package com.mapmory.backend.common.monitoring;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MockClock;
import io.micrometer.core.instrument.simple.SimpleConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.server.observation.DefaultServerRequestObservationConvention;
import org.springframework.http.server.observation.ServerRequestObservationContext;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class HttpServerSloObservationHandlerTest {

    private static final String INVITATION_PATTERN = "/api/v1/invitations/{tokenOrCode}";

    private final MockClock clock = new MockClock();
    private SimpleMeterRegistry meterRegistry;
    private ObservationRegistry observationRegistry;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry(SimpleConfig.DEFAULT, clock);
        observationRegistry = ObservationRegistry.create();
        observationRegistry.observationConfig().observationHandler(new HttpServerSloObservationHandler(meterRegistry));
    }

    // le는 Prometheus 히스토그램과 같은 누적값이다. 0.5초 요청은 0.3초 버킷에는 없고 1초·2초·+Inf에 하나씩 세어진다.
    @Test
    void 요청_시간_이상인_모든_le_곧_기준_안에_끝난_le에_하나씩_더한다() {
        request("GET", "/api/v1/invitations/raw-token", INVITATION_PATTERN, 200, Duration.ofMillis(500));

        assertThat(meterRegistry.get(HttpServerSloObservationHandler.METRIC_NAME).tag("le", "0.3").counter().count())
                .isZero();
        assertThat(count("1")).isEqualTo(1);
        assertThat(count("2")).isEqualTo(1);
        assertThat(count("+Inf")).isEqualTo(1);
    }

    // CloudWatch Agent는 시계열마다 첫 값을 버린다. le 시계열이 +Inf와 같은 요청에서 생겨야 첫 값이 분자·분모에서 함께 빠진다.
    @Test
    void 기준을_넘은_요청도_모든_le_카운터를_0으로_만든다() {
        request("GET", "/api/v1/travel-records", "/api/v1/travel-records", 200, Duration.ofMillis(2_500));

        assertThat(meterRegistry.find(HttpServerSloObservationHandler.METRIC_NAME).counters())
                .extracting(counter -> counter.getId().getTag("le") + "=" + counter.count())
                .containsExactlyInAnyOrder("0.3=0.0", "1=0.0", "2=0.0", "+Inf=1.0");
    }

    @Test
    void 기준과_같은_시간은_그_le에_세고_2초를_넘으면_Inf에만_센다() {
        request("GET", "/api/v1/invitations/a", INVITATION_PATTERN, 200, Duration.ofMillis(300));
        request("GET", "/api/v1/invitations/b", INVITATION_PATTERN, 200, Duration.ofMillis(2_500));

        assertThat(count("0.3")).isEqualTo(1);
        assertThat(count("1")).isEqualTo(1);
        assertThat(count("2")).isEqualTo(1);
        assertThat(count("+Inf")).isEqualTo(2);
    }

    // 초대 토큰처럼 경로에 비밀 값이 있어도 uri 태그에는 원본 경로가 아니라 경로 패턴이 남는다.
    @Test
    void uri는_원본_경로가_아니라_경로_패턴이고_status_exception은_태그로_두지_않는다() {
        request("GET", "/api/v1/invitations/raw-secret-token", INVITATION_PATTERN, 404, Duration.ofMillis(10));

        assertThat(meterRegistry.find(HttpServerSloObservationHandler.METRIC_NAME).counters())
                .hasSize(4)
                .allSatisfy(counter -> {
                    assertThat(counter.getId().getTag("uri")).isEqualTo(INVITATION_PATTERN);
                    assertThat(counter.getId().getTag("method")).isEqualTo("GET");
                    assertThat(counter.getId().getTag("outcome")).isEqualTo("CLIENT_ERROR");
                    assertThat(counter.getId().getTag("slo_class")).isEqualTo("other");
                    assertThat(counter.getId().getTag("status")).isNull();
                    assertThat(counter.getId().getTag("exception")).isNull();
                });
    }

    @Test
    void 조회_저장_인증_API는_slo_class가_붙는다() {
        request("GET", "/api/v1/travel-records/7", "/api/v1/travel-records/{travelRecordId}", 200, Duration.ofMillis(10));
        request("PUT", "/api/v2/travel-records/7", "/api/v2/travel-records/{travelRecordId}", 200, Duration.ofMillis(10));
        request("POST", "/api/v1/auth/token/refresh", "/api/v1/auth/token/refresh", 200, Duration.ofMillis(10));

        assertThat(meterRegistry.find(HttpServerSloObservationHandler.METRIC_NAME).tag("le", "+Inf").counters())
                .extracting(counter -> counter.getId().getTag("uri") + " " + counter.getId().getTag("slo_class"))
                .containsExactlyInAnyOrder(
                        "/api/v1/travel-records/{travelRecordId} read",
                        "/api/v2/travel-records/{travelRecordId} write",
                        "/api/v1/auth/token/refresh auth");
    }

    // 첫 수집값은 CloudWatch Agent가 버린다. 기동할 때 0으로 만들어 두어야 배포 직후 첫(콜드) 요청이 빠지지 않는다.
    @Test
    void 기동할_때_SLO_대상_경로의_카운터를_0으로_미리_만든다() {
        new HttpServerSloObservationHandler(meterRegistry).registerSloRoutes();

        int expected = SloClass.ROUTES.size() * HttpServerSloObservationHandler.PRE_REGISTERED_OUTCOMES.size() * 4;
        assertThat(meterRegistry.find(HttpServerSloObservationHandler.METRIC_NAME).counters())
                .hasSize(expected)
                .allSatisfy(counter -> assertThat(counter.count()).isZero());
        assertThat(meterRegistry.get(HttpServerSloObservationHandler.METRIC_NAME)
                .tags("uri", "/api/v2/travel-records", "method", "POST", "outcome", "SERVER_ERROR", "le", "2")
                .counter().getId().getTag("slo_class")).isEqualTo("write");
    }

    @Test
    void 미리_만든_카운터에_실제_요청이_그대로_더해진다() {
        HttpServerSloObservationHandler handler = new HttpServerSloObservationHandler(meterRegistry);
        handler.registerSloRoutes();

        request("GET", "/api/v1/travel-records", "/api/v1/travel-records", 200, Duration.ofMillis(100));

        assertThat(meterRegistry.find(HttpServerSloObservationHandler.METRIC_NAME)
                .tags("uri", "/api/v1/travel-records", "method", "GET", "outcome", "SUCCESS").counters())
                .extracting(counter -> counter.getId().getTag("le") + "=" + counter.count())
                .containsExactlyInAnyOrder("0.3=1.0", "1=1.0", "2=1.0", "+Inf=1.0");
    }

    @Test
    void 서버_요청이_아닌_Observation은_세지_않는다() {
        Observation.start("mapmory.other", observationRegistry).stop();

        assertThat(meterRegistry.find(HttpServerSloObservationHandler.METRIC_NAME).counters()).isEmpty();
    }

    @Test
    void 공통_태그_version은_SLO_카운터에서만_뗀다() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        registry.config().commonTags("version", "1.2.3");
        registry.config().meterFilter(new MetricsConfiguration().httpServerSloWithoutVersion());

        Counter.builder(HttpServerSloObservationHandler.METRIC_NAME).tag("le", "+Inf").register(registry);
        Counter.builder("other.counter").register(registry);

        assertThat(registry.get(HttpServerSloObservationHandler.METRIC_NAME).counter().getId().getTag("version"))
                .isNull();
        assertThat(registry.get("other.counter").counter().getId().getTag("version")).isEqualTo("1.2.3");
    }

    private void request(String method, String path, String pattern, int status, Duration elapsed) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        MockHttpServletResponse response = new MockHttpServletResponse();
        ServerRequestObservationContext context = new ServerRequestObservationContext(request, response);
        Observation observation = Observation.createNotStarted(
                new DefaultServerRequestObservationConvention(), () -> context, observationRegistry).start();
        context.setPathPattern(pattern);
        response.setStatus(status);
        clock.add(elapsed);
        observation.stop();
    }

    private double count(String le) {
        return meterRegistry.find(HttpServerSloObservationHandler.METRIC_NAME).tag("le", le).counters().stream()
                .mapToDouble(Counter::count)
                .sum();
    }
}
