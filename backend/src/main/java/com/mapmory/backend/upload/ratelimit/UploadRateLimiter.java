package com.mapmory.backend.upload.ratelimit;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.mapmory.backend.common.exception.BusinessException;
import com.mapmory.backend.upload.UploadErrorCode;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.TimeMeter;
import java.time.Duration;

/**
 * 회원별 업로드 URL 발급량을 센다. 한 사람이 저장 비용을 끝없이 늘리지 못하게 한다. (#481, ADR 0018)
 *
 * <p>요청 하나에 파일이 여러 개 들어오므로 요청 횟수가 아니라 파일 수를 센다. 남은 양보다 많이 요청하면 전부 거절하고 한도를 쓰지 않는다.
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
        if (!buckets.get(memberId, ignored -> newBucket()).tryConsume(fileCount)) {
            throw new BusinessException(UploadErrorCode.UPLOAD_RATE_LIMITED);
        }
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
