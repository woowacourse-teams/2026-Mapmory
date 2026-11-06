package com.mapmory.backend.place.infrastructure.google.guard;

import static com.mapmory.backend.support.LogEvents.field;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.mapmory.backend.IntegrationTest;
import com.mapmory.backend.common.exception.BusinessException;
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
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

class GoogleRequestLimiterIntegrationTest extends IntegrationTest {

    @Autowired
    MySQLSelectForUpdateBasedProxyManager<String> bucketManager;

    @Autowired
    HikariDataSource dataSource;

    @Test
    void 자동완성과_상세_조회의_월_한도를_DB에_따로_센다() {
        GoogleRequestLimiter first = limiter(2, 1, 10_000);

        first.reserve(GoogleEndpoint.AUTOCOMPLETE);
        first.reserve(GoogleEndpoint.DETAILS);

        GoogleRequestLimiter second = limiter(2, 1, 10_000);
        second.reserve(GoogleEndpoint.AUTOCOMPLETE);
        assertError(() -> second.reserve(GoogleEndpoint.AUTOCOMPLETE), "PLACE_SEARCH_BUDGET_EXHAUSTED");
        assertError(() -> second.reserve(GoogleEndpoint.DETAILS), "PLACE_PROVIDER_BUDGET_EXHAUSTED");
    }

    @Test
    void 역지오코딩_월_한도는_상세_한도와_다른_값을_쓴다() {
        GoogleRateLimitProperties properties = new GoogleRateLimitProperties(5, 5, 2, 10_000);
        GoogleRateLimitConfig config = new GoogleRateLimitConfig();

        assertThat(config.googleGeocodeBudget(properties).getBandwidths()[0].getCapacity()).isEqualTo(2);
        assertThat(config.googleDetailsBudget(properties).getBandwidths()[0].getCapacity()).isEqualTo(5);
    }

    @Test
    void 역지오코딩은_상세_조회와_월_한도를_따로_센다() {
        GoogleRequestLimiter limiter = limiter(2, 1, 10_000);

        limiter.reserve(GoogleEndpoint.DETAILS);
        limiter.reserve(GoogleEndpoint.GEOCODE);

        assertError(() -> limiter.reserve(GoogleEndpoint.DETAILS), "PLACE_PROVIDER_BUDGET_EXHAUSTED");
        assertError(() -> limiter.reserve(GoogleEndpoint.GEOCODE), "PLACE_PROVIDER_BUDGET_EXHAUSTED");
    }

    @Test
    void 월_한도의_80퍼센트를_쓴_순간과_다_쓴_순간에_한_번씩_ERROR_로그를_남긴다() {
        GoogleRequestLimiter limiter = limiter(5, 5, 10_000);
        Logger logger = (Logger) LoggerFactory.getLogger(GoogleRequestLimiter.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            for (int i = 0; i < 5; i++) {
                limiter.reserve(GoogleEndpoint.AUTOCOMPLETE);
            }
            assertError(() -> limiter.reserve(GoogleEndpoint.AUTOCOMPLETE), "PLACE_SEARCH_BUDGET_EXHAUSTED");
            limiter.reserve(GoogleEndpoint.DETAILS);
        } finally {
            logger.detachAppender(appender);
        }

        // 운영의 ERROR 로그 알람이 Discord로 알린다. 한도에 걸린 요청과 상세 1건은 로그를 남기지 않는다.
        assertThat(appender.list).allSatisfy(event -> assertThat(event.getLevel().toString()).isEqualTo("ERROR"));
        assertThat(appender.list).extracting(event -> field(event, "budget") + ":" + field(event, "remaining"))
                .containsExactly("autocomplete:1", "autocomplete:0");
    }

    @Test
    void 무료_사용량에_가까워진_순간에_한_번_ERROR_로그를_남기고_막지는_않는다() {
        GoogleRequestLimiter limiter = limiter(10, 10, 10_000, 3);
        Logger logger = (Logger) LoggerFactory.getLogger(GoogleRequestLimiter.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            for (int call = 1; call <= 5; call++) {
                limiter.reserve(GoogleEndpoint.GEOCODE);
                assertThat(appender.list).hasSize(call < 3 ? 0 : 1);
            }
        } finally {
            logger.detachAppender(appender);
        }

        assertThat(appender.list)
                .extracting(event -> field(event, "event") + ":" + field(event, "budget") + ":" + field(event, "used")
                        + ":" + event.getLevel())
                .containsExactly("GOOGLE_PLACES_FREE_USAGE:geocode:3:ERROR");
    }

    @Test
    void 돌려받아_같은_사용량을_다시_지나도_무료_사용량_알림은_한_달에_한_번이다() {
        GoogleRequestLimiter limiter = limiter(10, 10, 10_000, 2);
        Logger logger = (Logger) LoggerFactory.getLogger(GoogleRequestLimiter.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            limiter.reserve(GoogleEndpoint.GEOCODE);
            limiter.reserve(GoogleEndpoint.GEOCODE);
            limiter.refund(GoogleEndpoint.GEOCODE);
            limiter.reserve(GoogleEndpoint.GEOCODE);
        } finally {
            logger.detachAppender(appender);
        }

        assertThat(appender.list).extracting(event -> field(event, "event"))
                .containsExactly("GOOGLE_PLACES_FREE_USAGE");
    }

    // 배포 전에 9,000으로 만든 이번 달 버킷이 남아 있어도 올린 상한까지 쓸 수 있어야 한다.
    @Test
    void 상한을_올려_배포하면_이미_만든_이번_달_버킷에도_새_상한을_적용한다() {
        // 이전 코드처럼 버전 없이 상한 2로 만들고 다 쓴 버킷
        BucketConfiguration old = BucketConfiguration.builder()
                .addLimit(bandwidth -> bandwidth.capacity(2).refillIntervally(2, Duration.ofDays(31)))
                .build();
        bucketManager.getProxy("google:autocomplete:" + YearMonth.now(ZoneOffset.UTC), () -> old).tryConsume(2);

        GoogleRequestLimiter after = limiter(4, 2, 10_000);
        after.reserve(GoogleEndpoint.AUTOCOMPLETE);
        after.reserve(GoogleEndpoint.AUTOCOMPLETE);
        assertError(() -> after.reserve(GoogleEndpoint.AUTOCOMPLETE), "PLACE_SEARCH_BUDGET_EXHAUSTED");
    }

    @Test
    void 속도_제한에_걸린_요청은_월_한도를_쓰지_않는다() {
        GoogleRequestLimiter limiter = limiter(2, 1, 1);

        limiter.reserve(GoogleEndpoint.AUTOCOMPLETE);
        assertError(() -> limiter.reserve(GoogleEndpoint.AUTOCOMPLETE), "PLACE_RATE_LIMITED");
        assertError(() -> limiter.reserve(GoogleEndpoint.DETAILS), "PLACE_RATE_LIMITED");

        assertThat(availableThisMonth("google:autocomplete:", 2)).isEqualTo(1);
        assertThat(availableThisMonth("google:details:", 1)).isEqualTo(1);
    }

    @Test
    void 속도_제한에_걸린_요청은_커넥션_풀이_고갈돼도_커넥션_없이_거절한다() throws SQLException {
        GoogleRequestLimiter limiter = limiter(2, 1, 1);
        limiter.reserve(GoogleEndpoint.AUTOCOMPLETE);

        List<Connection> held = holdAllConnections();
        try {
            assertError(() -> limiter.reserve(GoogleEndpoint.AUTOCOMPLETE), "PLACE_RATE_LIMITED");
        } finally {
            for (Connection connection : held) {
                connection.close();
            }
        }
    }

    @Test
    void 돌려준_만큼_월_한도를_다시_쓴다() {
        GoogleRequestLimiter limiter = limiter(1, 1, 10_000);
        limiter.reserve(GoogleEndpoint.AUTOCOMPLETE);
        limiter.reserve(GoogleEndpoint.DETAILS);

        limiter.refund(GoogleEndpoint.AUTOCOMPLETE);
        limiter.refund(GoogleEndpoint.DETAILS);

        limiter.reserve(GoogleEndpoint.AUTOCOMPLETE);
        limiter.reserve(GoogleEndpoint.DETAILS);
        assertError(() -> limiter.reserve(GoogleEndpoint.AUTOCOMPLETE), "PLACE_SEARCH_BUDGET_EXHAUSTED");
        assertError(() -> limiter.reserve(GoogleEndpoint.DETAILS), "PLACE_PROVIDER_BUDGET_EXHAUSTED");
    }

    @Test
    void 돌려줘도_월_한도보다_많아지지_않는다() {
        GoogleRequestLimiter limiter = limiter(2, 1, 10_000);

        limiter.refund(GoogleEndpoint.AUTOCOMPLETE);
        limiter.refund(GoogleEndpoint.DETAILS);

        assertThat(availableThisMonth("google:autocomplete:", 2)).isEqualTo(2);
        assertThat(availableThisMonth("google:details:", 1)).isEqualTo(1);
    }

    // 돌려주기는 Google 장애 응답을 만드는 중에 한다. 돌려주기에 실패했다고 503 대신 500을 내지 않는다.
    @Test
    void 한도_저장소가_장애여도_돌려주기_실패는_응답을_바꾸지_않는다() throws SQLException {
        GoogleRequestLimiter limiter = limiter(2, 1, 10_000);

        List<Connection> held = holdAllConnections();
        try {
            assertThatCode(() -> limiter.refund(GoogleEndpoint.AUTOCOMPLETE)).doesNotThrowAnyException();
        } finally {
            for (Connection connection : held) {
                connection.close();
            }
        }
    }

    // 응답의 PLACE_PROVIDER_UNAVAILABLE은 WARN이라, 저장소 장애는 여기서 ERROR로 알린다.
    @Test
    void 한도_저장소가_장애면_Google을_부르지_않고_ERROR로_알린다() throws SQLException {
        GoogleRequestLimiter limiter = limiter(2, 1, 10_000);
        Logger logger = (Logger) LoggerFactory.getLogger(GoogleRequestLimiter.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        List<Connection> held = holdAllConnections();
        try {
            assertError(() -> limiter.reserve(GoogleEndpoint.AUTOCOMPLETE), "PLACE_PROVIDER_UNAVAILABLE");
        } finally {
            for (Connection connection : held) {
                connection.close();
            }
            logger.detachAppender(appender);
        }

        assertThat(appender.list).extracting(event -> field(event, "event") + ":" + event.getLevel())
                .containsExactly("PLACE_RATE_LIMIT_STORE_UNAVAILABLE:ERROR");
    }

    private GoogleRequestLimiter limiter(int autocompletePerMonth, int detailsPerMonth, int requestsPerMinute) {
        return limiter(autocompletePerMonth, detailsPerMonth, requestsPerMinute, Long.MAX_VALUE);
    }

    private GoogleRequestLimiter limiter(
            int autocompletePerMonth, int detailsPerMonth, int requestsPerMinute, long freeAlertAt) {
        GoogleRateLimitProperties properties =
                new GoogleRateLimitProperties(autocompletePerMonth, detailsPerMonth, detailsPerMonth, requestsPerMinute);
        GoogleRateLimitConfig config = new GoogleRateLimitConfig();
        return new GoogleRequestLimiter(
                bucketManager,
                config.googleRequestPace(properties),
                config.googleAutocompleteBudget(properties),
                config.googleDetailsBudget(properties),
                config.googleGeocodeBudget(properties),
                freeAlertAt,
                clock
        );
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
