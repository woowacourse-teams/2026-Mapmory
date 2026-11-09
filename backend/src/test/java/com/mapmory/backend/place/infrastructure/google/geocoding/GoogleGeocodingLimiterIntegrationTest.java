package com.mapmory.backend.place.infrastructure.google.geocoding;

import static com.mapmory.backend.support.LogEvents.field;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.mapmory.backend.IntegrationTest;
import com.mapmory.backend.common.exception.BusinessException;
import com.mapmory.backend.place.infrastructure.google.resilience.GoogleMonthlyBudget;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.mysql.MySQLSelectForUpdateBasedProxyManager;
import java.time.Duration;
import java.time.YearMonth;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

class GoogleGeocodingLimiterIntegrationTest extends IntegrationTest {

    @Autowired
    MySQLSelectForUpdateBasedProxyManager<String> bucketManager;

    @Test
    void 월_한도를_장소_상세와_다른_키로_센다() {
        GoogleGeocodingLimiter limiter = limiter(1, 10_000, Long.MAX_VALUE);

        limiter.reserve();

        assertError(limiter::reserve, "PLACE_PROVIDER_BUDGET_EXHAUSTED");
        assertThat(availableThisMonth("google:geocode:", 1)).isZero();
        assertThat(availableThisMonth("google:details:", 1)).isEqualTo(1);
    }

    @Test
    void 분당_한도에_걸린_요청은_월_한도를_쓰지_않는다() {
        GoogleGeocodingLimiter limiter = limiter(2, 1, Long.MAX_VALUE);

        limiter.reserve();

        assertError(limiter::reserve, "PLACE_RATE_LIMITED");
        assertThat(availableThisMonth("google:geocode:", 2)).isEqualTo(1);
    }

    @Test
    void 무료_사용량에_가까워진_순간에_한_번_ERROR_로그를_남기고_막지는_않는다() {
        GoogleGeocodingLimiter limiter = limiter(10, 10_000, 3);
        Logger logger = (Logger) LoggerFactory.getLogger(GoogleMonthlyBudget.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            for (int call = 1; call <= 5; call++) {
                limiter.reserve();
                assertThat(appender.list).hasSize(call < 3 ? 0 : 1);
            }
        } finally {
            logger.detachAppender(appender);
        }

        assertThat(appender.list)
                .extracting(event -> field(event, "event") + ":" + field(event, "budget") + ":" + field(event, "used")
                        + ":" + event.getLevel())
                .containsExactly("GOOGLE_GEOCODING_FREE_USAGE:geocode:3:ERROR");
    }

    @Test
    void 돌려받아_같은_사용량을_다시_지나도_무료_사용량_알림은_한_달에_한_번이다() {
        GoogleGeocodingLimiter limiter = limiter(10, 10_000, 2);
        Logger logger = (Logger) LoggerFactory.getLogger(GoogleMonthlyBudget.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            limiter.reserve();
            limiter.reserve();
            limiter.refund();
            limiter.reserve();
        } finally {
            logger.detachAppender(appender);
        }

        assertThat(appender.list).extracting(event -> field(event, "event"))
                .containsExactly("GOOGLE_GEOCODING_FREE_USAGE");
    }

    private GoogleGeocodingLimiter limiter(int perMonth, int requestsPerMinute, long freeAlertAt) {
        return GoogleGeocodingConfig.limiter(bucketManager,
                new GoogleGeocodingProperties.RateLimit(requestsPerMinute, perMonth, freeAlertAt), clock);
    }

    // 월 한도 키는 UTC 기준 연월이다(예: google:geocode:2026-11).
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
