package com.mapmory.backend.upload.ratelimit;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.mapmory.backend.common.exception.RateLimitedException;
import com.mapmory.backend.upload.UploadErrorCode;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.TimeMeter;
import java.text.NumberFormat;
import java.time.Duration;
import java.util.Locale;

/**
 * 회원별 업로드 URL 발급량을 센다. 한 사람이 저장 비용을 끝없이 늘리지 못하게 한다. (#481, ADR 0018)
 *
 * <p>요청 하나에 파일이 여러 개 들어오므로 요청 횟수가 아니라 파일 수를 센다. 남은 양보다 많이 요청하면 전부 거절하고 한도를 쓰지 않으며,
 * 다시 할 수 있을 때까지 남은 초를 함께 알린다. 앱은 오류 문구(detail)를 그대로 보여 주므로 문구에도 남은 시간을 넣는다.
 *
 * <p>하루 한도만 둔다. 저장 비용은 하루 한도로 막히고, 시간 한도는 여행 사진을 한 번에 몰아서 정리하는 사람만 막았다.
 * 1시간 300장 한도에서 25분 동안 기록 16개를 저장한 사용자가 막혀 앱을 지운 일이 있었다(2026-11-10).
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
            long retryAfterSeconds = retryAfterSeconds(probe);
            throw new RateLimitedException(
                    UploadErrorCode.UPLOAD_RATE_LIMITED,
                    detail(retryAfterSeconds),
                    retryAfterSeconds
            );
        }
    }

    private String detail(long retryAfterSeconds) {
        String limit = NumberFormat.getIntegerInstance(Locale.KOREA).format(properties.filesPerMemberPerDay());
        return "하루에 올릴 수 있는 사진 수(%s장)를 넘었습니다. %s 뒤에 다시 저장해 주세요."
                .formatted(limit, waitText(retryAfterSeconds));
    }

    // 분 단위로 올려 말한다. 알려 준 시간보다 일찍 다시 눌러 또 막히지 않게 한다.
    static String waitText(long retryAfterSeconds) {
        long minutes = Math.max(1, Math.ceilDiv(retryAfterSeconds, 60));
        long hours = minutes / 60;
        long restMinutes = minutes % 60;
        if (hours == 0) {
            return restMinutes + "분";
        }
        if (restMinutes == 0) {
            return hours + "시간";
        }
        return hours + "시간 " + restMinutes + "분";
    }

    // 요청이 하루 한도보다 크면 기다려도 통과할 수 없어 Bucket4j가 무한대를 돌려준다. 이때는 하루로 끊는다.
    private static long retryAfterSeconds(ConsumptionProbe probe) {
        long nanosPerSecond = Duration.ofSeconds(1).toNanos();
        return Math.min(Math.ceilDiv(probe.getNanosToWaitForRefill(), nanosPerSecond), Duration.ofDays(1).toSeconds());
    }

    private Bucket newBucket() {
        return Bucket.builder()
                .withCustomTimePrecision(timeMeter)
                .addLimit(limit -> limit.capacity(properties.filesPerMemberPerDay())
                        .refillIntervally(properties.filesPerMemberPerDay(), Duration.ofDays(1)))
                .build();
    }
}
