package com.mapmory.backend.group.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mapmory.backend.common.exception.BusinessException;
import com.mapmory.backend.group.exception.GroupErrorCode;
import io.github.bucket4j.TimeMeter;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class MemberInvitationRateLimiterTest {

    private final FakeTimeMeter time = new FakeTimeMeter();
    private final MemberInvitationRateLimiter rateLimiter =
            new MemberInvitationRateLimiter(new InvitationRateLimitProperties(2, 3), time);

    @Test
    void 일분_한도를_넘으면_막고_일분이_지나면_다시_된다() {
        rateLimiter.check(1L);
        rateLimiter.check(1L);

        assertRateLimited(1L);

        time.advance(Duration.ofMinutes(1));
        assertThatCode(() -> rateLimiter.check(1L)).doesNotThrowAnyException();
    }

    @Test
    void 하루_한도를_넘으면_일분이_지나도_막는다() {
        rateLimiter.check(1L);
        rateLimiter.check(1L);
        time.advance(Duration.ofMinutes(1));
        rateLimiter.check(1L);
        time.advance(Duration.ofMinutes(1));

        assertRateLimited(1L);

        time.advance(Duration.ofDays(1));
        assertThatCode(() -> rateLimiter.check(1L)).doesNotThrowAnyException();
    }

    @Test
    void 회원마다_따로_센다() {
        rateLimiter.check(1L);
        rateLimiter.check(1L);

        assertThatCode(() -> rateLimiter.check(2L)).doesNotThrowAnyException();
    }

    private void assertRateLimited(Long memberId) {
        assertThatThrownBy(() -> rateLimiter.check(memberId))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(GroupErrorCode.INVITATION_RATE_LIMITED));
    }

    private static final class FakeTimeMeter implements TimeMeter {

        private long nanos;

        void advance(Duration duration) {
            nanos += duration.toNanos();
        }

        @Override
        public long currentTimeNanos() {
            return nanos;
        }

        @Override
        public boolean isWallClockBased() {
            return false;
        }
    }
}
