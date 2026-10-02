package com.mapmory.backend.place.infrastructure.geoapify;

import com.mapmory.backend.common.exception.BusinessException;
import com.mapmory.backend.place.application.PlaceErrorCode;
import com.mapmory.backend.place.application.port.PlaceRateLimitPort;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.BucketExceptions.BucketExecutionException;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.springframework.stereotype.Component;

@Component
public class GeoapifyRequestLimiter implements PlaceRateLimitPort {

    private static final String SEARCH_BUDGET = "geoapify:search:";
    private static final String SELECTION_BUDGET = "geoapify:selection:";
    private static final String TOTAL_BUDGET = "geoapify:total:";
    private static final String REQUEST_PACE = "geoapify:pace";

    private final ProxyManager<String> bucketManager;
    private final BucketConfiguration memberSearchLimit;
    private final BucketConfiguration memberSelectionLimit;
    private final BucketConfiguration searchBudget;
    private final BucketConfiguration selectionBudget;
    private final BucketConfiguration totalBudget;
    private final BucketConfiguration requestPace;

    public GeoapifyRequestLimiter(
            ProxyManager<String> bucketManager,
            GeoapifyRateLimitProperties properties
    ) {
        this.bucketManager = bucketManager;
        this.memberSearchLimit = limit(properties.searchesPerMemberPerMinute(), Duration.ofMinutes(1));
        this.memberSelectionLimit = limit(properties.selectionsPerMemberPerDay(), Duration.ofDays(1));
        this.searchBudget = limit(properties.searchesPerDay(), Duration.ofDays(1));
        this.selectionBudget = limit(properties.selectionsPerDay(), Duration.ofDays(1));
        this.totalBudget = limit(properties.requestsPerDay(), Duration.ofDays(1));
        this.requestPace = BucketConfiguration.builder()
                .addLimit(bandwidth -> bandwidth.capacity(1)
                        .refillGreedy(1, Duration.ofSeconds(1).dividedBy(properties.requestsPerSecond())))
                .build();
    }

    @Override
    public void checkSearch(Long memberId) {
        consume("geoapify:member:" + requireMemberId(memberId),
                memberSearchLimit, PlaceErrorCode.PLACE_RATE_LIMITED);
    }

    @Override
    public void checkSelection(Long memberId) {
        String today = LocalDate.now(ZoneOffset.UTC).toString();
        consume(SELECTION_BUDGET + "member:" + requireMemberId(memberId) + ":" + today,
                memberSelectionLimit, PlaceErrorCode.PLACE_RATE_LIMITED);
        consume(SELECTION_BUDGET + today, selectionBudget,
                PlaceErrorCode.PLACE_SELECTION_BUDGET_EXHAUSTED);
    }

    public void reserveSearch() {
        // 검색 한도를 먼저 소진해 상세 조회와 기록 저장용 전체 한도를 보존한다.
        String today = LocalDate.now(ZoneOffset.UTC).toString();
        consume(SEARCH_BUDGET + today, searchBudget, PlaceErrorCode.PLACE_SEARCH_BUDGET_EXHAUSTED);
        consume(REQUEST_PACE, requestPace, PlaceErrorCode.PLACE_RATE_LIMITED);
        consume(TOTAL_BUDGET + today, totalBudget, PlaceErrorCode.PLACE_PROVIDER_BUDGET_EXHAUSTED);
    }

    public void reserveDetails() {
        consume(REQUEST_PACE, requestPace, PlaceErrorCode.PLACE_RATE_LIMITED);
        consume(TOTAL_BUDGET + LocalDate.now(ZoneOffset.UTC), totalBudget,
                PlaceErrorCode.PLACE_PROVIDER_BUDGET_EXHAUSTED);
    }

    private void consume(String key, BucketConfiguration configuration, PlaceErrorCode exhaustedError) {
        try {
            if (!bucketManager.getProxy(key, () -> configuration).tryConsume(1)) {
                throw new BusinessException(exhaustedError);
            }
        } catch (BucketExecutionException exception) {
            // 한도 저장소가 장애일 때 외부 API를 호출하면 사용량을 통제할 수 없다.
            throw new BusinessException(PlaceErrorCode.PLACE_PROVIDER_UNAVAILABLE);
        }
    }

    private static BucketConfiguration limit(int capacity, Duration period) {
        return BucketConfiguration.builder()
                .addLimit(bandwidth -> bandwidth.capacity(capacity).refillIntervally(capacity, period))
                .build();
    }

    private static Long requireMemberId(Long memberId) {
        if (memberId == null) {
            throw new IllegalArgumentException("인증된 회원 ID가 필요합니다.");
        }
        return memberId;
    }
}
