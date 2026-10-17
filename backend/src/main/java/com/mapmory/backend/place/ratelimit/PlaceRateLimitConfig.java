package com.mapmory.backend.place.ratelimit;

import static io.github.bucket4j.distributed.ExpirationAfterWriteStrategy.basedOnTimeForRefillingBucketUpToMax;

import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.distributed.jdbc.PrimaryKeyMapper;
import io.github.bucket4j.mysql.Bucket4jMySQL;
import io.github.bucket4j.mysql.MySQLSelectForUpdateBasedProxyManager;
import java.time.Duration;
import javax.sql.DataSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 장소 한도 버킷을 만든다. 서버 여러 대가 함께 세야 하는 한도는 DB 버킷 저장소(place_rate_limit_bucket)에 둔다.
 * 회원별 한도의 확인 순서와 에러 코드는 {@link MemberPlaceRateLimiter}가 정한다.
 */
@Configuration
@EnableScheduling
@EnableConfigurationProperties(PlaceRateLimitProperties.class)
class PlaceRateLimitConfig {

    static final String MEMBER_SEARCH_LIMIT = "memberSearchLimit";
    static final String MEMBER_SELECTION_LIMIT = "memberSelectionLimit";

    // 회원별 한도와 Google 월 한도가 함께 쓰는 DB 버킷 저장소다.
    @Bean
    MySQLSelectForUpdateBasedProxyManager<String> placeRateLimitBucketManager(DataSource dataSource) {
        return Bucket4jMySQL.selectForUpdateBasedBuilder(dataSource)
                .primaryKeyMapper(PrimaryKeyMapper.STRING)
                .table("place_rate_limit_bucket")
                .expirationAfterWrite(basedOnTimeForRefillingBucketUpToMax(Duration.ofHours(1)))
                .build();
    }

    @Bean(MEMBER_SEARCH_LIMIT)
    BucketConfiguration memberSearchLimit(PlaceRateLimitProperties properties) {
        return limit(properties.searchesPerMemberPerMinute(), Duration.ofMinutes(1));
    }

    @Bean(MEMBER_SELECTION_LIMIT)
    BucketConfiguration memberSelectionLimit(PlaceRateLimitProperties properties) {
        return limit(properties.selectionsPerMemberPerDay(), Duration.ofDays(1));
    }

    private static BucketConfiguration limit(int capacity, Duration period) {
        return BucketConfiguration.builder()
                .addLimit(bandwidth -> bandwidth.capacity(capacity).refillIntervally(capacity, period))
                .build();
    }
}
