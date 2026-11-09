package com.mapmory.backend.upload.ratelimit;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(UploadRateLimitProperties.class)
class UploadRateLimitConfig {

    @Bean
    UploadRateLimiter uploadRateLimiter(UploadRateLimitProperties properties) {
        return new UploadRateLimiter(properties);
    }
}
