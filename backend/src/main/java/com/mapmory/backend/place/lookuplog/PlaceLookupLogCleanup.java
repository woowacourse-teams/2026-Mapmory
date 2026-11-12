package com.mapmory.backend.place.lookuplog;

import com.mapmory.backend.common.scheduling.MaintenanceSchedulingConfig;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 하루 한 번 30일이 지난 장소 검색·선택 기록을 지운다. API를 받지 않는 렌더러 서버에서는 돌지 않는다.
 */
@Component
@ConditionalOnProperty(prefix = "notification.outbox", name = "enabled", havingValue = "true", matchIfMissing = true)
class PlaceLookupLogCleanup {

    static final Duration RETENTION = Duration.ofDays(30);

    private static final Logger log = LoggerFactory.getLogger(PlaceLookupLogCleanup.class);
    private static final int BATCH_SIZE = 1_000;

    private final PlaceLookupLogRepository repository;
    private final Clock clock;

    PlaceLookupLogCleanup(PlaceLookupLogRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Scheduled(cron = "0 45 19 * * *", zone = "UTC", scheduler = MaintenanceSchedulingConfig.SCHEDULER)
    void cleanUp() {
        try {
            int removed = run();
            log.atInfo()
                    .addKeyValue("event", "PLACE_LOOKUP_LOG_CLEANUP_DONE")
                    .addKeyValue("removed", removed)
                    .log("30일이 지난 장소 검색·선택 기록을 지웠다");
        } catch (RuntimeException exception) {
            // 지운 묶음은 그대로 두고 남은 행은 다음 날 다시 지운다.
            log.atWarn()
                    .addKeyValue("event", "PLACE_LOOKUP_LOG_CLEANUP_FAILED")
                    .setCause(exception)
                    .log("장소 검색·선택 기록을 지우지 못했다");
        }
    }

    int run() {
        LocalDateTime before = LocalDateTime.now(clock).minus(RETENTION);
        int removed = 0;
        int batch;
        do {
            batch = repository.deleteOlderThan(before, BATCH_SIZE);
            removed += batch;
        } while (batch == BATCH_SIZE);
        return removed;
    }
}
