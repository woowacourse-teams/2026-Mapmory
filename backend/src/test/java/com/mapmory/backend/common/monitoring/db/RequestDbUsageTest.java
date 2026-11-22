package com.mapmory.backend.common.monitoring.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.mock;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import javax.sql.DataSource;
import net.ttddyy.dsproxy.support.ProxyDataSourceBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.server.observation.DefaultServerRequestObservationConvention;
import org.springframework.http.server.observation.ServerRequestObservationContext;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * 서버 요청 Observation → 요청 스레드의 {@link RequestDbUsage} → datasource-proxy로 감싼 DataSource의 SQL 실행까지 한 번에 본다.
 * JDBC는 Mockito 대역이고, SQL 하나가 걸리는 시간은 가짜 시계를 앞으로 돌려 만든다.
 */
class RequestDbUsageTest {

    private static final String LIST = "/api/v1/travel-records";
    private static final String INVITATION_PATTERN = "/api/v1/invitations/{tokenOrCode}";
    private static final String SECRET = "secret-nickname";

    private final AtomicLong nanoTime = new AtomicLong();
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private final Logger requestLogger = (Logger) LoggerFactory.getLogger(SlowRequestObservationHandler.class);
    private final Logger queryLogger = (Logger) LoggerFactory.getLogger(RequestDbUsageListener.class);
    private ObservationRegistry observationRegistry;
    private DataSource dataSource;

    @BeforeEach
    void setUp() throws SQLException {
        appender.start();
        requestLogger.addAppender(appender);
        queryLogger.addAppender(appender);
        configure(new DbMonitoringProperties.SlowRequest(Duration.ZERO, 0), new DbMonitoringProperties.SlowQuery(null));
    }

    @AfterEach
    void tearDown() {
        requestLogger.detachAppender(appender);
        queryLogger.detachAppender(appender);
    }

    @Test
    void SQL을_3번_실행한_요청은_dbQueryCount가_3이고_DB_시간은_그_합이다() {
        request("GET", LIST, LIST, () -> {
            query("select id from travel_record where member_id = ?", 5);
            query("select id from tag where member_id = ?", 7);
            query("select id from region where id = ?", 3);
        });

        assertThat(slowRequests()).singleElement().satisfies(fields -> {
            assertThat(fields).containsEntry("dbQueryCount", 3).containsEntry("dbTimeMs", 15L);
        });
    }

    @Test
    void 동시에_들어온_두_요청의_SQL_수는_섞이지_않는다() throws Exception {
        CyclicBarrier barrier = new CyclicBarrier(2);
        CompletableFuture<Void> one = CompletableFuture.runAsync(() ->
                request("GET", "/api/v1/one", "/api/v1/one", () -> interleaved(barrier, 2)));
        CompletableFuture<Void> two = CompletableFuture.runAsync(() ->
                request("GET", "/api/v1/two", "/api/v1/two", () -> interleaved(barrier, 5)));
        CompletableFuture.allOf(one, two).get(5, TimeUnit.SECONDS);

        assertThat(slowRequests())
                .extracting(fields -> fields.get("uri") + "=" + fields.get("dbQueryCount"))
                .containsExactlyInAnyOrder("/api/v1/one=2", "/api/v1/two=5");
    }

    @Test
    void 요청_스레드_밖의_SQL은_세지_않고_다음_요청에도_더해지지_않는다() {
        query("select id from push_outbox", 1);

        request("GET", LIST, LIST, () -> query("select id from travel_record", 1));

        assertThat(slowRequests()).singleElement().satisfies(fields ->
                assertThat(fields).containsEntry("dbQueryCount", 1));
    }

    @Test
    void 요청_기준을_비우면_SLO_기준_미만의_요청은_남기지_않는다() throws SQLException {
        configure(new DbMonitoringProperties.SlowRequest(null, 0), new DbMonitoringProperties.SlowQuery(null));

        // 목록 조회는 read(300ms), 통계는 other(1s)다.
        request("GET", LIST, LIST, () -> advance(300));
        request("GET", "/api/v1/travel-records/statistics", "/api/v1/travel-records/statistics", () -> advance(999));

        assertThat(slowRequests()).isEmpty();
    }

    @Test
    void SLO_기준을_넘은_요청은_한_줄_남긴다() throws SQLException {
        configure(new DbMonitoringProperties.SlowRequest(null, 0), new DbMonitoringProperties.SlowQuery(null));

        request("GET", LIST, LIST, () -> {
            query("select id from travel_record", 1);
            advance(300);
        });

        assertThat(slowRequests()).singleElement().satisfies(fields -> assertThat(fields)
                .containsEntry("method", "GET")
                .containsEntry("uri", LIST)
                .containsEntry("status", 200)
                .containsEntry("sloClass", "read")
                .containsEntry("latencyMs", 301L)
                .containsEntry("dbQueryCount", 1)
                .containsEntry("dbTimeMs", 1L));
    }

    @Test
    void 요청_기준을_0으로_두면_모든_요청을_남긴다() {
        request("GET", LIST, LIST, () -> { });
        request("POST", "/api/v1/auth/logout", "/api/v1/auth/logout", () -> { });

        assertThat(slowRequests()).hasSize(2);
    }

    @Test
    void 쿼리_수_기준이_0이면_쿼리를_많이_실행해도_쿼리_수로는_남기지_않는다() throws SQLException {
        configure(new DbMonitoringProperties.SlowRequest(null, 0), new DbMonitoringProperties.SlowQuery(null));

        request("GET", LIST, LIST, () -> {
            for (int i = 0; i < 50; i++) {
                query("select id from tag where id = ?", 0);
            }
        });

        assertThat(slowRequests()).isEmpty();
    }

    @Test
    void 쿼리_수_기준을_넘으면_시간이_짧아도_남긴다() throws SQLException {
        configure(new DbMonitoringProperties.SlowRequest(null, 2), new DbMonitoringProperties.SlowQuery(null));

        request("GET", LIST, LIST, () -> {
            query("select id from tag where id = ?", 0);
            query("select id from tag where id = ?", 0);
        });
        request("GET", LIST, LIST, () -> {
            query("select id from tag where id = ?", 0);
            query("select id from tag where id = ?", 0);
            query("select id from tag where id = ?", 0);
        });

        assertThat(slowRequests()).singleElement().satisfies(fields ->
                assertThat(fields).containsEntry("dbQueryCount", 3));
    }

    // 초대 토큰은 경로에 들어간다. 로그에는 경로 패턴만 남아야 한다.
    @Test
    void uri는_원본_경로가_아니라_경로_패턴이다() {
        request("GET", "/api/v1/invitations/raw-invitation-token", INVITATION_PATTERN, () ->
                query("select id from group_invitation where token_hash = ?", 1));

        assertThat(slowRequests()).singleElement().satisfies(fields ->
                assertThat(fields).containsEntry("uri", INVITATION_PATTERN).containsEntry("sloClass", "other"));
        assertThat(appender.list).allSatisfy(event -> assertThat(rendered(event)).doesNotContain("raw-invitation-token"));
    }

    @Test
    void SQL_하나가_100ms를_넘으면_종류와_테이블만_남기고_SQL_원문과_파라미터는_남기지_않는다() throws SQLException {
        request("GET", "/api/v1/invitations/raw-invitation-token", INVITATION_PATTERN, () -> {
            query("select m1_0.id, m1_0.nickname from member m1_0 where m1_0.nickname = '" + SECRET + "'", 101);
            query("select id from member where id = ?", 100);
        });

        assertThat(fieldsOf("SLOW_QUERY")).singleElement().satisfies(fields -> assertThat(fields)
                .containsOnlyKeys("event", "uri", "elapsedMs", "queryType", "table")
                .containsEntry("uri", INVITATION_PATTERN)
                .containsEntry("elapsedMs", 101L)
                .containsEntry("queryType", "SELECT")
                .containsEntry("table", "member"));
        assertThat(appender.list).allSatisfy(event -> assertThat(rendered(event))
                .doesNotContain(SECRET)
                .doesNotContain("m1_0.nickname")
                .doesNotContain("raw-invitation-token"));
    }

    @Test
    void 컨트롤러를_찾기_전에_실행한_느린_SQL의_uri는_UNKNOWN이다() {
        request("GET", "/api/v1/invitations/raw-invitation-token", null, () ->
                query("select id from member where id = ?", 150));

        assertThat(fieldsOf("SLOW_QUERY")).singleElement().satisfies(fields ->
                assertThat(fields).containsEntry("uri", "UNKNOWN"));
    }

    private void configure(DbMonitoringProperties.SlowRequest slowRequest, DbMonitoringProperties.SlowQuery slowQuery)
            throws SQLException {
        observationRegistry = ObservationRegistry.create();
        observationRegistry.observationConfig()
                .observationHandler(new SlowRequestObservationHandler(slowRequest, nanoTime::get));
        dataSource = ProxyDataSourceBuilder.create(fakeDataSource())
                .listener(new RequestDbUsageListener(slowQuery, nanoTime::get))
                .build();
    }

    // executeQuery 한 번이 statement.setLong으로 받은 값(ms)만큼 걸린 것처럼 시계를 돌린다.
    private DataSource fakeDataSource() throws SQLException {
        DataSource fake = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        given(fake.getConnection()).willReturn(connection);
        given(connection.prepareStatement(anyString())).willAnswer(invocation -> {
            PreparedStatement statement = mock(PreparedStatement.class);
            AtomicLong elapsedMillis = new AtomicLong();
            willAnswer(set -> {
                elapsedMillis.set(set.getArgument(1));
                return null;
            }).given(statement).setLong(eq(99), anyLong());
            given(statement.executeQuery()).willAnswer(execute -> {
                advance(elapsedMillis.get());
                return mock(ResultSet.class);
            });
            return statement;
        });
        return fake;
    }

    private void query(String sql, long elapsedMillis) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, SECRET + "-param");
            statement.setLong(99, elapsedMillis);
            statement.executeQuery();
        } catch (SQLException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private void interleaved(CyclicBarrier barrier, int queries) {
        for (int i = 0; i < 5; i++) {
            if (i < queries) {
                query("select id from travel_record", 1);
            }
            try {
                barrier.await(5, TimeUnit.SECONDS);
            } catch (Exception exception) {
                throw new IllegalStateException(exception);
            }
        }
    }

    private void advance(long millis) {
        nanoTime.addAndGet(TimeUnit.MILLISECONDS.toNanos(millis));
    }

    private void request(String method, String path, String pattern, Runnable work) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        MockHttpServletResponse response = new MockHttpServletResponse();
        ServerRequestObservationContext context = new ServerRequestObservationContext(request, response);
        Observation observation = Observation.createNotStarted(
                new DefaultServerRequestObservationConvention(), () -> context, observationRegistry).start();
        context.setPathPattern(pattern);
        work.run();
        response.setStatus(200);
        observation.stop();
    }

    private List<Map<String, Object>> slowRequests() {
        return fieldsOf("SLOW_REQUEST");
    }

    private List<Map<String, Object>> fieldsOf(String event) {
        return appender.list.stream()
                .map(RequestDbUsageTest::fields)
                .filter(fields -> event.equals(fields.get("event")))
                .toList();
    }

    private static Map<String, Object> fields(ILoggingEvent event) {
        return event.getKeyValuePairs() == null ? Map.of() : event.getKeyValuePairs().stream()
                .collect(Collectors.toMap(pair -> pair.key, pair -> pair.value));
    }

    private static String rendered(ILoggingEvent event) {
        return event.getFormattedMessage() + " " + fields(event).values();
    }
}
