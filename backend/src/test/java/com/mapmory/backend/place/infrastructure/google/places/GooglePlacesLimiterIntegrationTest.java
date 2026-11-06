package com.mapmory.backend.place.infrastructure.google.places;

import static com.mapmory.backend.support.LogEvents.field;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.mapmory.backend.IntegrationTest;
import com.mapmory.backend.common.exception.BusinessException;
import com.mapmory.backend.place.infrastructure.google.resilience.GoogleMonthlyBudget;
import com.zaxxer.hikari.HikariDataSource;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.mysql.MySQLSelectForUpdateBasedProxyManager;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

class GooglePlacesLimiterIntegrationTest extends IntegrationTest {

    @Autowired
    MySQLSelectForUpdateBasedProxyManager<String> bucketManager;

    @Autowired
    HikariDataSource dataSource;

    @Test
    void 자동완성과_상세_조회의_월_한도를_DB에_따로_센다() {
        GooglePlacesLimiter first = limiter(2, 1, 10_000);

        first.reserve(GooglePlacesEndpoint.AUTOCOMPLETE);
        first.reserve(GooglePlacesEndpoint.DETAILS);

        GooglePlacesLimiter second = limiter(2, 1, 10_000);
        second.reserve(GooglePlacesEndpoint.AUTOCOMPLETE);
        assertError(() -> second.reserve(GooglePlacesEndpoint.AUTOCOMPLETE), "PLACE_SEARCH_BUDGET_EXHAUSTED");
        assertError(() -> second.reserve(GooglePlacesEndpoint.DETAILS), "PLACE_PROVIDER_BUDGET_EXHAUSTED");
    }

    @Test
    void 월_한도의_80퍼센트를_쓴_순간과_다_쓴_순간에_한_번씩_ERROR_로그를_남긴다() {
        GooglePlacesLimiter limiter = limiter(5, 5, 10_000);
        Logger logger = (Logger) LoggerFactory.getLogger(GoogleMonthlyBudget.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            for (int i = 0; i < 5; i++) {
                limiter.reserve(GooglePlacesEndpoint.AUTOCOMPLETE);
            }
            assertError(() -> limiter.reserve(GooglePlacesEndpoint.AUTOCOMPLETE), "PLACE_SEARCH_BUDGET_EXHAUSTED");
            limiter.reserve(GooglePlacesEndpoint.DETAILS);
        } finally {
            logger.detachAppender(appender);
        }

        // 운영의 ERROR 로그 알람이 Discord로 알린다. 한도에 걸린 요청과 상세 1건은 로그를 남기지 않는다.
        assertThat(appender.list).allSatisfy(event -> assertThat(event.getLevel().toString()).isEqualTo("ERROR"));
        assertThat(appender.list).extracting(event -> field(event, "budget") + ":" + field(event, "remaining"))
                .containsExactly("autocomplete:1", "autocomplete:0");
    }

    // 배포 전에 9,000으로 만든 이번 달 버킷이 남아 있어도 올린 상한까지 쓸 수 있어야 한다.
    @Test
    void 상한을_올려_배포하면_이미_만든_이번_달_버킷에도_새_상한을_적용한다() {
        // 이전 코드처럼 버전 없이 상한 2로 만들고 다 쓴 버킷
        BucketConfiguration old = BucketConfiguration.builder()
                .addLimit(bandwidth -> bandwidth.capacity(2).refillIntervally(2, Duration.ofDays(31)))
                .build();
        bucketManager.getProxy("google:autocomplete:" + YearMonth.now(ZoneOffset.UTC), () -> old).tryConsume(2);

        GooglePlacesLimiter after = limiter(4, 2, 10_000);
        after.reserve(GooglePlacesEndpoint.AUTOCOMPLETE);
        after.reserve(GooglePlacesEndpoint.AUTOCOMPLETE);
        assertError(() -> after.reserve(GooglePlacesEndpoint.AUTOCOMPLETE), "PLACE_SEARCH_BUDGET_EXHAUSTED");
    }

    @Test
    void 속도_제한에_걸린_요청은_월_한도를_쓰지_않는다() {
        GooglePlacesLimiter limiter = limiter(2, 1, 1);

        limiter.reserve(GooglePlacesEndpoint.AUTOCOMPLETE);
        assertError(() -> limiter.reserve(GooglePlacesEndpoint.AUTOCOMPLETE), "PLACE_RATE_LIMITED");
        assertError(() -> limiter.reserve(GooglePlacesEndpoint.DETAILS), "PLACE_RATE_LIMITED");

        assertThat(availableThisMonth("google:autocomplete:", 2)).isEqualTo(1);
        assertThat(availableThisMonth("google:details:", 1)).isEqualTo(1);
    }

    @Test
    void 속도_제한에_걸린_요청은_커넥션_풀이_고갈돼도_커넥션_없이_거절한다() throws SQLException {
        GooglePlacesLimiter limiter = limiter(2, 1, 1);
        limiter.reserve(GooglePlacesEndpoint.AUTOCOMPLETE);

        List<Connection> held = holdAllConnections();
        try {
            assertError(() -> limiter.reserve(GooglePlacesEndpoint.AUTOCOMPLETE), "PLACE_RATE_LIMITED");
        } finally {
            for (Connection connection : held) {
                connection.close();
            }
        }
    }

    @Test
    void 돌려준_만큼_월_한도를_다시_쓴다() {
        GooglePlacesLimiter limiter = limiter(1, 1, 10_000);
        limiter.reserve(GooglePlacesEndpoint.AUTOCOMPLETE);
        limiter.reserve(GooglePlacesEndpoint.DETAILS);

        limiter.refund(GooglePlacesEndpoint.AUTOCOMPLETE);
        limiter.refund(GooglePlacesEndpoint.DETAILS);

        limiter.reserve(GooglePlacesEndpoint.AUTOCOMPLETE);
        limiter.reserve(GooglePlacesEndpoint.DETAILS);
        assertError(() -> limiter.reserve(GooglePlacesEndpoint.AUTOCOMPLETE), "PLACE_SEARCH_BUDGET_EXHAUSTED");
        assertError(() -> limiter.reserve(GooglePlacesEndpoint.DETAILS), "PLACE_PROVIDER_BUDGET_EXHAUSTED");
    }

    @Test
    void 돌려줘도_월_한도보다_많아지지_않는다() {
        GooglePlacesLimiter limiter = limiter(2, 1, 10_000);

        limiter.refund(GooglePlacesEndpoint.AUTOCOMPLETE);
        limiter.refund(GooglePlacesEndpoint.DETAILS);

        assertThat(availableThisMonth("google:autocomplete:", 2)).isEqualTo(2);
        assertThat(availableThisMonth("google:details:", 1)).isEqualTo(1);
    }

    // 돌려주기는 Google 장애 응답을 만드는 중에 한다. 돌려주기에 실패했다고 503 대신 500을 내지 않는다.
    @Test
    void 한도_저장소가_장애여도_돌려주기_실패는_응답을_바꾸지_않는다() throws SQLException {
        GooglePlacesLimiter limiter = limiter(2, 1, 10_000);

        List<Connection> held = holdAllConnections();
        try {
            assertThatCode(() -> limiter.refund(GooglePlacesEndpoint.AUTOCOMPLETE)).doesNotThrowAnyException();
        } finally {
            for (Connection connection : held) {
                connection.close();
            }
        }
    }

    // 응답의 PLACE_PROVIDER_UNAVAILABLE은 WARN이라, 저장소 장애는 여기서 ERROR로 알린다.
    @Test
    void 한도_저장소가_장애면_Google을_부르지_않고_ERROR로_알린다() throws SQLException {
        GooglePlacesLimiter limiter = limiter(2, 1, 10_000);
        Logger logger = (Logger) LoggerFactory.getLogger(GoogleMonthlyBudget.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        List<Connection> held = holdAllConnections();
        try {
            assertError(() -> limiter.reserve(GooglePlacesEndpoint.AUTOCOMPLETE), "PLACE_PROVIDER_UNAVAILABLE");
        } finally {
            for (Connection connection : held) {
                connection.close();
            }
            logger.detachAppender(appender);
        }

        assertThat(appender.list).extracting(event -> field(event, "event") + ":" + event.getLevel())
                .containsExactly("PLACE_RATE_LIMIT_STORE_UNAVAILABLE:ERROR");
    }

    private GooglePlacesLimiter limiter(int autocompletePerMonth, int detailsPerMonth, int requestsPerMinute) {
        return limiter(autocompletePerMonth, detailsPerMonth, requestsPerMinute, Long.MAX_VALUE);
    }

    private GooglePlacesLimiter limiter(
            int autocompletePerMonth, int detailsPerMonth, int requestsPerMinute, long freeAlertAt) {
        return GooglePlacesConfig.limiter(bucketManager,
                new GooglePlacesProperties.RateLimit(requestsPerMinute, Map.of(
                        GooglePlacesEndpoint.AUTOCOMPLETE, autocompletePerMonth, GooglePlacesEndpoint.DETAILS, detailsPerMonth),
                        freeAlertAt),
                clock);
    }

    private List<Connection> holdAllConnections() throws SQLException {
        List<Connection> held = new ArrayList<>();
        for (int i = 0; i < dataSource.getMaximumPoolSize(); i++) {
            held.add(dataSource.getConnection());
        }
        return held;
    }

    // 월 한도 키는 UTC 기준 연월이다(예: google:autocomplete:2026-10).
    private long availableThisMonth(String prefix, int capacity) {
        BucketConfiguration monthly = BucketConfiguration.builder()
                .addLimit(bandwidth -> bandwidth.capacity(capacity).refillIntervally(capacity, Duration.ofDays(31)))
                .build();
        return bucketManager.getProxy(prefix + YearMonth.now(ZoneOffset.UTC), () -> monthly).getAvailableTokens();
    }

    private void assertError(Runnable action, String expectedCode) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode().code())
                        .isEqualTo(expectedCode));
    }
}
