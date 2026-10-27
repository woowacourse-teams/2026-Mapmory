package com.mapmory.backend.group.ratelimit;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.mapmory.backend.common.exception.BusinessException;
import com.mapmory.backend.group.exception.GroupErrorCode;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.TimeMeter;
import java.time.Duration;

/**
 * 회원별 초대 미리보기·수락 횟수를 센다. 6자리 코드를 아무거나 넣어 보며 살아 있는 초대를 찾는 것을 막는다.
 *
 * <p>버킷은 메모리에 둔다. 초대 API를 부를 때마다 DB 커넥션을 쓰지 않게 하려는 것이다.
 * 서버마다 따로 세므로 서버를 늘리면 한도도 서버 수만큼 늘어난다(게스트 로그인 한도와 같은 전제).
 * 하루 동안 부르지 않은 회원의 버킷은 버리고, 버킷 수에도 상한을 둔다.
 */
class MemberInvitationRateLimiter {

    private static final int MAX_TRACKED_MEMBERS = 100_000;

    private final Cache<Long, Bucket> buckets = Caffeine.newBuilder()
            .expireAfterAccess(Duration.ofDays(1))
            .maximumSize(MAX_TRACKED_MEMBERS)
            .build();
    private final InvitationRateLimitProperties properties;
    private final TimeMeter timeMeter;

    MemberInvitationRateLimiter(InvitationRateLimitProperties properties) {
        this(properties, TimeMeter.SYSTEM_MILLISECONDS);
    }

    MemberInvitationRateLimiter(InvitationRateLimitProperties properties, TimeMeter timeMeter) {
        this.properties = properties;
        this.timeMeter = timeMeter;
    }

    void check(Long memberId) {
        if (!buckets.get(memberId, ignored -> newBucket()).tryConsume(1)) {
            throw new BusinessException(GroupErrorCode.INVITATION_RATE_LIMITED);
        }
    }

    private Bucket newBucket() {
        return Bucket.builder()
                .withCustomTimePrecision(timeMeter)
                .addLimit(limit -> limit.capacity(properties.perMemberPerMinute())
                        .refillIntervally(properties.perMemberPerMinute(), Duration.ofMinutes(1)))
                .addLimit(limit -> limit.capacity(properties.perMemberPerDay())
                        .refillIntervally(properties.perMemberPerDay(), Duration.ofDays(1)))
                .build();
    }
}
