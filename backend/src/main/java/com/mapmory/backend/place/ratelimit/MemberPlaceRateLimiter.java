package com.mapmory.backend.place.ratelimit;

import com.mapmory.backend.common.exception.BusinessException;
import com.mapmory.backend.place.exception.PlaceErrorCode;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.BucketExceptions.BucketExecutionException;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import java.time.Clock;
import java.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * 회원별 장소 검색·선택 횟수를 DB 버킷으로 센다. 장소 API 인터셉터가 컨트롤러에 들어가기 전에 부른다.
 * Google 호출 한도는 {@code GooglePlacesClient}가 따로 확인한다.
 */
@Component
class MemberPlaceRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(MemberPlaceRateLimiter.class);

    private static final String SEARCH = "place:search:member:";
    private static final String SELECTION = "place:selection:member:";

    private final ProxyManager<String> bucketManager;
    private final BucketConfiguration searchLimit;
    private final BucketConfiguration selectionLimit;
    private final Clock clock;

    MemberPlaceRateLimiter(
            ProxyManager<String> bucketManager,
            @Qualifier(PlaceRateLimitConfig.MEMBER_SEARCH_LIMIT) BucketConfiguration searchLimit,
            @Qualifier(PlaceRateLimitConfig.MEMBER_SELECTION_LIMIT) BucketConfiguration selectionLimit,
            Clock clock
    ) {
        this.bucketManager = bucketManager;
        this.searchLimit = searchLimit;
        this.selectionLimit = selectionLimit;
        this.clock = clock;
    }

    public void checkSearch(Long memberId) {
        consume(SEARCH + requireMemberId(memberId), searchLimit);
    }

    public void checkSelection(Long memberId) {
        // 날짜를 키에 넣어 UTC 날짜가 바뀌면 새로 센다.
        consume(SELECTION + requireMemberId(memberId) + ":" + LocalDate.now(clock), selectionLimit);
    }

    private void consume(String key, BucketConfiguration configuration) {
        try {
            if (!bucketManager.getProxy(key, () -> configuration).tryConsume(1)) {
                throw new BusinessException(PlaceErrorCode.PLACE_RATE_LIMITED);
            }
        } catch (BucketExecutionException exception) {
            // 한도 저장소가 장애일 때 외부 API를 호출하면 사용량을 통제할 수 없다.
            log.atError()
                    .addKeyValue("event", "PLACE_RATE_LIMIT_STORE_UNAVAILABLE")
                    .setCause(exception)
                    .log("장소 한도 저장소를 쓰지 못했다");
            throw new BusinessException(PlaceErrorCode.PLACE_PROVIDER_UNAVAILABLE);
        }
    }

    private static Long requireMemberId(Long memberId) {
        if (memberId == null) {
            throw new IllegalArgumentException("인증된 회원 ID가 필요합니다.");
        }
        return memberId;
    }
}
