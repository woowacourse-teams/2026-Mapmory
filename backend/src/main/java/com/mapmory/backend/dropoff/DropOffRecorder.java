package com.mapmory.backend.dropoff;

import com.mapmory.backend.common.exception.BusinessException;
import com.mapmory.backend.dropoff.DropOffLog.Kind;
import com.mapmory.backend.dropoff.DropOffLog.Outcome;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 장소 검색·선택 결과와 거절된 사진 업로드를 drop_off_log에 남긴다.
 *
 * <p>분석용이라 남기지 못해도 원래 응답은 그대로 나가야 한다. 실패하면 WARN만 남기고 넘어간다.
 * 장소는 Google을 부른 뒤에 짧게 한 줄만 넣으므로 외부 호출 동안 커넥션을 잡지 않는다.
 *
 * <p>같은 회원이 같은 오류로 거듭 막히면(한도를 넘긴 뒤 재시도하는 앱 등) 1분에 한 줄만 남긴다.
 * 한도 거절은 DB를 쓰지 않으려는 길이라, 거절마다 INSERT하면 그 의도가 깨진다. 서버마다 메모리에서 센다.
 */
@Component
public class DropOffRecorder {

    static final String UNEXPECTED = "UNEXPECTED";

    private static final Logger log = LoggerFactory.getLogger(DropOffRecorder.class);

    private static final Duration FAILURE_DEDUP = Duration.ofMinutes(1);
    private static final int MAX_TRACKED_FAILURES = 100_000;

    private final DropOffLogRepository repository;
    private final Clock clock;
    private final Cache<String, Boolean> recentFailures = Caffeine.newBuilder()
            .expireAfterWrite(FAILURE_DEDUP)
            .maximumSize(MAX_TRACKED_FAILURES)
            .build();

    public DropOffRecorder(DropOffLogRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public void searched(DropOffContext context, String sessionToken, String query, int resultCount) {
        save(context, Kind.SEARCH, resultCount == 0 ? Outcome.NO_RESULTS : Outcome.RESULTS, null,
                entry -> entry.sessionToken(sessionToken).query(query).resultCount(resultCount));
    }

    public void searchFailed(DropOffContext context, String sessionToken, String query, String errorCode) {
        save(context, Kind.SEARCH, Outcome.FAILED, errorCode,
                entry -> entry.sessionToken(sessionToken).query(query));
    }

    /** 지역을 못 찾았으면 regionCode는 null이다. 국가도 모르면 countryCode도 null이다. */
    public void selected(DropOffContext context, String sessionToken, String placeId, String countryCode,
                         String regionCode) {
        Outcome outcome = regionCode != null ? Outcome.REGION_FOUND
                : countryCode != null ? Outcome.REGION_NOT_FOUND
                : Outcome.COUNTRY_UNKNOWN;
        save(context, Kind.SELECT, outcome, null,
                entry -> entry.sessionToken(sessionToken).place(placeId, countryCode, regionCode));
    }

    public void selectFailed(DropOffContext context, String sessionToken, String placeId, String errorCode) {
        save(context, Kind.SELECT, Outcome.FAILED, errorCode,
                entry -> entry.sessionToken(sessionToken).place(placeId, null, null));
    }

    /** 거절된 사진 업로드 주소 요청. fileSize·contentType은 거절된 파일이고, 모르면 가장 큰 파일이다. */
    public void uploadRejected(DropOffContext context, int fileCount, Long fileSize, String contentType,
                               String errorCode) {
        save(context, Kind.UPLOAD, Outcome.FAILED, errorCode,
                entry -> entry.upload(fileCount, fileSize, contentType));
    }

    /** 우리 오류면 그 코드, 예상하지 못한 예외(500)면 UNEXPECTED. */
    public static String errorCodeOf(RuntimeException exception) {
        return exception instanceof BusinessException business ? business.getErrorCode().code() : UNEXPECTED;
    }

    // 같은 회원·종류·오류가 1분 안에 이미 남았으면 건너뛴다. 메모리만 쓴다.
    private boolean isRepeatedFailure(Long memberId, Kind kind, String errorCode) {
        String key = memberId + ":" + kind + ":" + errorCode;
        return recentFailures.asMap().putIfAbsent(key, Boolean.TRUE) != null;
    }

    private void save(DropOffContext context, Kind kind, Outcome outcome, String errorCode,
                      Function<DropOffLog, DropOffLog> fill) {
        if (context.memberId() == null) {
            // 장소·업로드 API는 인증이 필요해 실제 요청에는 회원이 있다. 회원 없이 부른 호출은 남기지 않는다.
            return;
        }
        if (outcome == Outcome.FAILED && isRepeatedFailure(context.memberId(), kind, errorCode)) {
            return;
        }
        try {
            repository.save(fill.apply(new DropOffLog(context, kind, outcome, LocalDateTime.now(clock)))
                    .errorCode(errorCode));
        } catch (RuntimeException exception) {
            log.atWarn()
                    .addKeyValue("event", "DROP_OFF_LOG_FAILED")
                    .addKeyValue("kind", kind)
                    .setCause(exception)
                    .log("막힌 지점 기록을 남기지 못했다");
        }
    }
}
