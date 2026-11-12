package com.mapmory.backend.upload.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mapmory.backend.common.exception.BusinessException;
import com.mapmory.backend.common.exception.RateLimitedException;
import com.mapmory.backend.upload.UploadErrorCode;
import io.github.bucket4j.TimeMeter;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class UploadRateLimiterTest {

    private final FakeTimeMeter time = new FakeTimeMeter();
    private final UploadRateLimiter rateLimiter = new UploadRateLimiter(new UploadRateLimitProperties(5), time);

    @Test
    void 요청_횟수가_아니라_파일_수를_센다() {
        rateLimiter.check(1L, 5);

        assertRateLimited(1L, 1);
    }

    @Test
    void 한_시간_안에_하루_한도까지_몰아서_올릴_수_있다() {
        rateLimiter.check(1L, 2);
        time.advance(Duration.ofMinutes(10));
        rateLimiter.check(1L, 2);
        time.advance(Duration.ofMinutes(10));

        assertThatCode(() -> rateLimiter.check(1L, 1)).doesNotThrowAnyException();
    }

    @Test
    void 하루_한도를_넘으면_막고_하루가_지나면_다시_된다() {
        rateLimiter.check(1L, 5);
        time.advance(Duration.ofHours(23));

        assertRateLimited(1L, 1);

        time.advance(Duration.ofHours(1));
        assertThatCode(() -> rateLimiter.check(1L, 1)).doesNotThrowAnyException();
    }

    @Test
    void 남은_양보다_많이_요청하면_거절하고_한도를_쓰지_않는다() {
        rateLimiter.check(1L, 4);

        assertRateLimited(1L, 2);

        assertThatCode(() -> rateLimiter.check(1L, 1)).doesNotThrowAnyException();
    }

    @Test
    void 회원마다_따로_센다() {
        rateLimiter.check(1L, 5);

        assertThatCode(() -> rateLimiter.check(2L, 5)).doesNotThrowAnyException();
    }

    @Test
    void 하루_한도에_걸리면_한도가_풀릴_때까지_남은_초를_알려_준다() {
        rateLimiter.check(1L, 5);
        time.advance(Duration.ofHours(2));

        assertThat(retryAfterSeconds(1L, 1)).isEqualTo(Duration.ofHours(22).toSeconds());
    }

    @Test
    void 알려_준_초가_지나면_다시_된다() {
        rateLimiter.check(1L, 5);
        time.advance(Duration.ofMinutes(30).plusNanos(1));

        long retryAfterSeconds = retryAfterSeconds(1L, 1);
        time.advance(Duration.ofSeconds(retryAfterSeconds));

        assertThatCode(() -> rateLimiter.check(1L, 1)).doesNotThrowAnyException();
    }

    @Test
    void 한도보다_많이_요청하면_하루를_넘겨_알려_주지_않는다() {
        assertThat(retryAfterSeconds(1L, 6)).isEqualTo(Duration.ofDays(1).toSeconds());
    }

    @Test
    void 막힌_문구에_한도와_다시_할_수_있을_때까지_남은_시간을_넣는다() {
        rateLimiter.check(1L, 5);
        time.advance(Duration.ofHours(20).plusMinutes(30));

        assertThatThrownBy(() -> rateLimiter.check(1L, 1))
                .isInstanceOfSatisfying(BusinessException.class, exception -> assertThat(exception.getDetail())
                        .isEqualTo("하루에 올릴 수 있는 사진 수(5장)를 넘었습니다. 3시간 30분 뒤에 다시 저장해 주세요."));
    }

    @Test
    void 남은_시간은_분_단위로_올려_말한다() {
        assertThat(UploadRateLimiter.waitText(1)).isEqualTo("1분");
        assertThat(UploadRateLimiter.waitText(60)).isEqualTo("1분");
        assertThat(UploadRateLimiter.waitText(61)).isEqualTo("2분");
        assertThat(UploadRateLimiter.waitText(3600)).isEqualTo("1시간");
        assertThat(UploadRateLimiter.waitText(3601)).isEqualTo("1시간 1분");
        assertThat(UploadRateLimiter.waitText(Duration.ofDays(1).toSeconds())).isEqualTo("24시간");
    }

    private long retryAfterSeconds(Long memberId, int fileCount) {
        assertRateLimited(memberId, fileCount);
        try {
            rateLimiter.check(memberId, fileCount);
        } catch (RateLimitedException exception) {
            return exception.getRetryAfterSeconds();
        }
        throw new AssertionError("한도에 걸려야 한다");
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
