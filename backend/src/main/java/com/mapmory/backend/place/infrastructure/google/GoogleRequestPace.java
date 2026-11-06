package com.mapmory.backend.place.infrastructure.google;

import com.mapmory.backend.common.exception.BusinessException;
import com.mapmory.backend.place.exception.PlaceErrorCode;
import io.github.bucket4j.Bucket;
import java.time.Duration;

/**
 * Google API 하나의 분당 한도. 짧은 시간에 몰린 요청이 월 한도를 한꺼번에 쓰지 못하게 하는 값이라 서버마다 메모리에서 센다.
 * 그래서 분당 한도에 걸린 요청은 DB 커넥션 없이 바로 거절된다. 월 한도보다 먼저 확인한다.
 */
public final class GoogleRequestPace {

    private final Bucket bucket;

    public GoogleRequestPace(int requestsPerMinute) {
        if (requestsPerMinute < 1) {
            throw new IllegalArgumentException("Google 분당 한도는 1 이상이어야 합니다.");
        }
        this.bucket = Bucket.builder()
                .addLimit(bandwidth -> bandwidth.capacity(requestsPerMinute)
                        .refillGreedy(requestsPerMinute, Duration.ofMinutes(1)))
                .build();
    }

    /** 몰린 요청을 막는 값이라 Google이 과금하지 않은 실패여도 돌려주지 않는다. */
    public void acquire() {
        if (!bucket.tryConsume(1)) {
            throw new BusinessException(PlaceErrorCode.PLACE_RATE_LIMITED);
        }
    }
}
