package com.mapmory.backend.place.infrastructure.geoapify;

import static io.github.bucket4j.distributed.ExpirationAfterWriteStrategy.basedOnTimeForRefillingBucketUpToMax;

import io.github.bucket4j.distributed.jdbc.PrimaryKeyMapper;
import io.github.bucket4j.mysql.Bucket4jMySQL;
import io.github.bucket4j.mysql.MySQLSelectForUpdateBasedProxyManager;
import java.time.Duration;
import javax.sql.DataSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@EnableConfigurationProperties(GeoapifyRateLimitProperties.class)
class GeoapifyRateLimitConfig {

    @Bean
    MySQLSelectForUpdateBasedProxyManager<String> geoapifyBucketManager(DataSource dataSource) {
        return Bucket4jMySQL.selectForUpdateBasedBuilder(dataSource)
                .primaryKeyMapper(PrimaryKeyMapper.STRING)
                .table("place_rate_limit_bucket")
                .expirationAfterWrite(basedOnTimeForRefillingBucketUpToMax(Duration.ofHours(1)))
                .build();
    }
}
