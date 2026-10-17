package com.mapmory.backend.place.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mapmory.backend.IntegrationTest;
import com.mapmory.backend.common.exception.BusinessException;
import io.github.bucket4j.mysql.MySQLSelectForUpdateBasedProxyManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class MemberPlaceRateLimiterIntegrationTest extends IntegrationTest {

    @Autowired
    MySQLSelectForUpdateBasedProxyManager<String> bucketManager;

    @Test
    void 회원별_검색_한도를_DB에서_센다() {
        MemberPlaceRateLimiter first = limiter(2, 1);

        first.checkSearch(1L);
        first.checkSearch(1L);
        assertError(() -> first.checkSearch(1L), "PLACE_RATE_LIMITED");
        first.checkSearch(2L);

        // 다른 서버도 같은 DB 버킷을 본다.
        MemberPlaceRateLimiter second = limiter(2, 1);
        assertError(() -> second.checkSearch(1L), "PLACE_RATE_LIMITED");
    }

    @Test
    void 선택은_회원별_하루_한도만_센다() {
        MemberPlaceRateLimiter limiter = limiter(2, 1);

        limiter.checkSelection(1L);
        assertError(() -> limiter.checkSelection(1L), "PLACE_RATE_LIMITED");

        // 전체 선택 한도는 두지 않는다. 다른 회원은 계속 고를 수 있다.
        for (long memberId = 2; memberId <= 10; memberId++) {
            limiter.checkSelection(memberId);
        }
    }

    private MemberPlaceRateLimiter limiter(int searchesPerMinute, int selectionsPerDay) {
        PlaceRateLimitProperties properties = new PlaceRateLimitProperties(searchesPerMinute, selectionsPerDay);
        PlaceRateLimitConfig config = new PlaceRateLimitConfig();
        return new MemberPlaceRateLimiter(
                bucketManager,
                config.memberSearchLimit(properties),
                config.memberSelectionLimit(properties)
        );
    }

    private void assertError(Runnable action, String expectedCode) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode().code())
                        .isEqualTo(expectedCode));
    }
}
