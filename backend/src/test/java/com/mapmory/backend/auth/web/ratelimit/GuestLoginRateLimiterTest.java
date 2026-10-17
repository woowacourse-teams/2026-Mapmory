package com.mapmory.backend.auth.web.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import com.mapmory.backend.support.MutableClock;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class GuestLoginRateLimiterTest {

    private static final Instant START = Instant.parse("2026-08-26T00:00:00Z");
    private static final Duration WINDOW = Duration.ofHours(1);

    private final MutableClock clock = new MutableClock(START);
    private final GuestLoginRateLimiter rateLimiter = new GuestLoginRateLimiter(
            new GuestLoginRateLimitProperties(2, WINDOW),
            clock
    );

    @Test
    void 한도까지는_허용하고_초과하면_거절한다() {
        assertThat(rateLimiter.tryAcquire("10.0.0.1")).isTrue();
        assertThat(rateLimiter.tryAcquire("10.0.0.1")).isTrue();

        assertThat(rateLimiter.tryAcquire("10.0.0.1")).isFalse();
    }

    @Test
    void 출처가_다르면_한도를_따로_센다() {
        rateLimiter.tryAcquire("10.0.0.1");
        rateLimiter.tryAcquire("10.0.0.1");

        assertThat(rateLimiter.tryAcquire("10.0.0.2")).isTrue();
    }

    @Test
    void 윈도우가_지나면_다시_허용한다() {
        rateLimiter.tryAcquire("10.0.0.1");
        rateLimiter.tryAcquire("10.0.0.1");
        assertThat(rateLimiter.tryAcquire("10.0.0.1")).isFalse();

        clock.advance(WINDOW);

        assertThat(rateLimiter.tryAcquire("10.0.0.1")).isTrue();
    }

    @Test
    void 윈도우가_지나기_전에는_계속_거절한다() {
        rateLimiter.tryAcquire("10.0.0.1");
        rateLimiter.tryAcquire("10.0.0.1");

        clock.advance(WINDOW.minusSeconds(1));

        assertThat(rateLimiter.tryAcquire("10.0.0.1")).isFalse();
    }
}
