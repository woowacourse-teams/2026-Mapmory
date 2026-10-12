package com.mapmory.backend.place.infrastructure.google;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mapmory.backend.IntegrationTest;
import com.mapmory.backend.common.exception.BusinessException;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.mysql.MySQLSelectForUpdateBasedProxyManager;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class GoogleRequestLimiterIntegrationTest extends IntegrationTest {

    @Autowired
    MySQLSelectForUpdateBasedProxyManager<String> bucketManager;

    @Test
    void 자동완성과_상세_조회의_하루_한도를_DB에_따로_센다() {
        GoogleRequestLimiter first = new GoogleRequestLimiter(bucketManager, 2, 1, 10_000);

        first.reserveAutocomplete();
        first.reserveDetails();

        GoogleRequestLimiter second = new GoogleRequestLimiter(bucketManager, 2, 1, 10_000);
        second.reserveAutocomplete();
        assertError(second::reserveAutocomplete, "PLACE_SEARCH_BUDGET_EXHAUSTED");
        assertError(second::reserveDetails, "PLACE_PROVIDER_BUDGET_EXHAUSTED");
    }

    @Test
    void 속도_제한에_걸린_요청은_하루_한도를_쓰지_않는다() {
        GoogleRequestLimiter limiter = new GoogleRequestLimiter(bucketManager, 2, 1, 1);

        limiter.reserveAutocomplete();
        assertError(limiter::reserveAutocomplete, "PLACE_RATE_LIMITED");
        assertError(limiter::reserveDetails, "PLACE_RATE_LIMITED");

        assertThat(availableToday("google:autocomplete:", 2)).isEqualTo(1);
        assertThat(availableToday("google:details:", 1)).isEqualTo(1);
    }

    private long availableToday(String prefix, int capacity) {
        BucketConfiguration daily = BucketConfiguration.builder()
                .addLimit(bandwidth -> bandwidth.capacity(capacity).refillIntervally(capacity, Duration.ofDays(1)))
                .build();
        return bucketManager.getProxy(prefix + LocalDate.now(ZoneOffset.UTC), () -> daily).getAvailableTokens();
    }

    private void assertError(Runnable action, String expectedCode) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode().code())
                        .isEqualTo(expectedCode));
    }
}
