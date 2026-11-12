package com.mapmory.backend.place.lookuplog;

import com.mapmory.backend.common.exception.BusinessException;
import com.mapmory.backend.place.lookuplog.PlaceLookupLog.Kind;
import com.mapmory.backend.place.lookuplog.PlaceLookupLog.Outcome;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 장소 검색·선택 결과를 place_lookup_log에 남긴다.
 *
 * <p>분석용이라 남기지 못해도 검색·선택 응답은 그대로 나가야 한다. 실패하면 WARN만 남기고 넘어간다.
 * Google을 부른 뒤에 짧게 한 줄만 넣으므로, 외부 호출 동안 커넥션을 잡지 않는다.
 */
@Component
public class PlaceLookupRecorder {

    static final String UNEXPECTED = "UNEXPECTED";

    private static final Logger log = LoggerFactory.getLogger(PlaceLookupRecorder.class);

    private final PlaceLookupLogRepository repository;
    private final Clock clock;

    public PlaceLookupRecorder(PlaceLookupLogRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public void searched(PlaceLookupContext context, String sessionToken, String query, int resultCount) {
        save(context, Kind.SEARCH, resultCount == 0 ? Outcome.NO_RESULTS : Outcome.RESULTS,
                entry -> entry.sessionToken(sessionToken).query(query).resultCount(resultCount));
    }

    public void searchFailed(PlaceLookupContext context, String sessionToken, String query, String errorCode) {
        save(context, Kind.SEARCH, Outcome.FAILED,
                entry -> entry.sessionToken(sessionToken).query(query).errorCode(errorCode));
    }

    /** 지역을 못 찾았으면 regionCode는 null이다. 국가도 모르면 countryCode도 null이다. */
    public void selected(PlaceLookupContext context, String sessionToken, String placeId, String countryCode,
                         String regionCode) {
        Outcome outcome = regionCode != null ? Outcome.REGION_FOUND
                : countryCode != null ? Outcome.REGION_NOT_FOUND
                : Outcome.COUNTRY_UNKNOWN;
        save(context, Kind.SELECT, outcome,
                entry -> entry.sessionToken(sessionToken).place(placeId, countryCode, regionCode));
    }

    public void selectFailed(PlaceLookupContext context, String sessionToken, String placeId, String errorCode) {
        save(context, Kind.SELECT, Outcome.FAILED,
                entry -> entry.sessionToken(sessionToken).place(placeId, null, null).errorCode(errorCode));
    }

    /** 우리 오류면 그 코드, 예상하지 못한 예외(500)면 UNEXPECTED. */
    public static String errorCodeOf(RuntimeException exception) {
        return exception instanceof BusinessException business ? business.getErrorCode().code() : UNEXPECTED;
    }

    private void save(PlaceLookupContext context, Kind kind, Outcome outcome,
                      Function<PlaceLookupLog, PlaceLookupLog> fill) {
        if (context.memberId() == null) {
            // 장소 API는 인증이 필요해 실제 요청에는 회원이 있다. 회원 없이 부른 호출은 남기지 않는다.
            return;
        }
        try {
            repository.save(fill.apply(new PlaceLookupLog(context, kind, outcome, LocalDateTime.now(clock))));
        } catch (RuntimeException exception) {
            log.atWarn()
                    .addKeyValue("event", "PLACE_LOOKUP_LOG_FAILED")
                    .addKeyValue("kind", kind)
                    .setCause(exception)
                    .log("장소 검색·선택 기록을 남기지 못했다");
        }
    }
}
