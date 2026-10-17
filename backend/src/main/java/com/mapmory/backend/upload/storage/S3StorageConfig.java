package com.mapmory.backend.upload.storage;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Configuration
@EnableConfigurationProperties(S3StorageProperties.class)
public class S3StorageConfig {

    // SDK 기본값은 시도마다 30초씩 4번까지 기다려, S3가 멈추면 호출 하나가 요청 스레드를 2분 가까이 쥔다.
    @Bean
    public S3Client s3Client(S3StorageProperties properties) {
        S3StorageProperties.Client client = properties.client();
        return S3Client.builder()
                .region(Region.of(properties.region()))
                .overrideConfiguration(override -> override
                        .apiCallAttemptTimeout(client.attemptTimeout())
                        .apiCallTimeout(client.totalTimeout())
                        .retryStrategy(retry -> retry.maxAttempts(client.maxAttempts())))
                .build();
    }

    @Bean
    public S3Presigner s3Presigner(S3StorageProperties properties) {
        return S3Presigner.builder()
                .region(Region.of(properties.region()))
                .build();
    }
}
