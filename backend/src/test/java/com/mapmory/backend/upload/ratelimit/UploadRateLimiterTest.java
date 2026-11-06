package com.mapmory.backend.upload.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mapmory.backend.common.exception.BusinessException;
import com.mapmory.backend.upload.UploadErrorCode;
import io.github.bucket4j.TimeMeter;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class UploadRateLimiterTest {

    private final FakeTimeMeter time = new FakeTimeMeter();
    private final UploadRateLimiter rateLimiter = new UploadRateLimiter(new UploadRateLimitProperties(3, 5), time);

    @Test
    void 요청_횟수가_아니라_파일_수를_센다() {
        rateLimiter.check(1L, 3);

        assertRateLimited(1L, 1);
    }

    @Test
    void 시간_한도를_넘으면_막고_한_시간이_지나면_다시_된다() {
        rateLimiter.check(1L, 3);

        time.advance(Duration.ofHours(1));

        assertThatCode(() -> rateLimiter.check(1L, 1)).doesNotThrowAnyException();
    }

    @Test
    void 하루_한도를_넘으면_한_시간이_지나도_막는다() {
        rateLimiter.check(1L, 3);
        time.advance(Duration.ofHours(1));
        rateLimiter.check(1L, 2);
        time.advance(Duration.ofHours(1));

        assertRateLimited(1L, 1);

        time.advance(Duration.ofDays(1));
        assertThatCode(() -> rateLimiter.check(1L, 1)).doesNotThrowAnyException();
    }

    @Test
    void 남은_양보다_많이_요청하면_거절하고_한도를_쓰지_않는다() {
        rateLimiter.check(1L, 2);

        assertRateLimited(1L, 2);

        assertThatCode(() -> rateLimiter.check(1L, 1)).doesNotThrowAnyException();
    }

    @Test
    void 회원마다_따로_센다() {
        rateLimiter.check(1L, 3);

        assertThatCode(() -> rateLimiter.check(2L, 3)).doesNotThrowAnyException();
    }

    private void assertRateLimited(Long memberId, int fileCount) {
        assertThatThrownBy(() -> rateLimiter.check(memberId, fileCount))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(UploadErrorCode.UPLOAD_RATE_LIMITED));
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
