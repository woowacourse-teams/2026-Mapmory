package com.mapmory.backend.upload.ratelimit;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.mapmory.backend.common.exception.RateLimitedException;
import com.mapmory.backend.upload.UploadErrorCode;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.TimeMeter;
import java.time.Duration;

/**
 * 회원별 업로드 URL 발급량을 센다. 한 사람이 저장 비용을 끝없이 늘리지 못하게 한다. (#481, ADR 0018)
 *
 * <p>요청 하나에 파일이 여러 개 들어오므로 요청 횟수가 아니라 파일 수를 센다. 남은 양보다 많이 요청하면 전부 거절하고 한도를 쓰지 않으며,
 * 다시 할 수 있을 때까지 남은 초를 함께 알린다.
 *
 * <p>버킷은 메모리에 둔다. 서버마다 따로 세고 재시작하면 처음부터 센다(초대 한도와 같은 전제).
 * 하루 동안 부르지 않은 회원의 버킷은 버리고, 버킷 수에도 상한을 둔다.
 */
public class UploadRateLimiter {

    private static final int MAX_TRACKED_MEMBERS = 100_000;

    private final Cache<Long, Bucket> buckets = Caffeine.newBuilder()
            .expireAfterAccess(Duration.ofDays(1))
            .maximumSize(MAX_TRACKED_MEMBERS)
            .build();
    private final UploadRateLimitProperties properties;
    private final TimeMeter timeMeter;

    UploadRateLimiter(UploadRateLimitProperties properties) {
        this(properties, TimeMeter.SYSTEM_MILLISECONDS);
    }

    UploadRateLimiter(UploadRateLimitProperties properties, TimeMeter timeMeter) {
        this.properties = properties;
        this.timeMeter = timeMeter;
    }

    public void check(Long memberId, int fileCount) {
        ConsumptionProbe probe = buckets.get(memberId, ignored -> newBucket()).tryConsumeAndReturnRemaining(fileCount);
        if (!probe.isConsumed()) {
            throw new RateLimitedException(UploadErrorCode.UPLOAD_RATE_LIMITED, retryAfterSeconds(probe));
        }
    }

    // 요청이 시간 한도보다 크면 기다려도 통과할 수 없어 Bucket4j가 무한대를 돌려준다. 이때는 하루로 끊는다.
    private static long retryAfterSeconds(ConsumptionProbe probe) {
        long nanosPerSecond = Duration.ofSeconds(1).toNanos();
        return Math.min(Math.ceilDiv(probe.getNanosToWaitForRefill(), nanosPerSecond), Duration.ofDays(1).toSeconds());
    }

    private Bucket newBucket() {
        return Bucket.builder()
                .withCustomTimePrecision(timeMeter)
                .addLimit(limit -> limit.capacity(properties.filesPerMemberPerHour())
                        .refillIntervally(properties.filesPerMemberPerHour(), Duration.ofHours(1)))
                .addLimit(limit -> limit.capacity(properties.filesPerMemberPerDay())
                        .refillIntervally(properties.filesPerMemberPerDay(), Duration.ofDays(1)))
                .build();
    }
}
