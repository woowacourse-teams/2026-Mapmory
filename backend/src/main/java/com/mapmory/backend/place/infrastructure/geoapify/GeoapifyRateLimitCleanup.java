package com.mapmory.backend.place.infrastructure.geoapify;

import io.github.bucket4j.mysql.MySQLSelectForUpdateBasedProxyManager;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
class GeoapifyRateLimitCleanup {

    private static final int BATCH_SIZE = 1_000;
    private final MySQLSelectForUpdateBasedProxyManager<String> bucketManager;

    GeoapifyRateLimitCleanup(MySQLSelectForUpdateBasedProxyManager<String> bucketManager) {
        this.bucketManager = bucketManager;
    }

    @Scheduled(cron = "0 30 4 * * *", zone = "UTC")
    void removeExpiredBuckets() {
        while (bucketManager.removeExpired(BATCH_SIZE) == BATCH_SIZE) {
            // 만료된 회원별 검색 버킷을 작은 배치로 정리한다.
        }
    }
}
