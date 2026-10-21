package com.mapmory.backend.recordmedia;

import com.mapmory.backend.upload.storage.PresignedUrlProvider;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 사진 조회 URL을 S3에서 바로 받게 할지, CloudFront를 거치게 할지 고른다.
 *
 * <p>공용 계정에서는 CloudFront 배포를 지울 수 없어서, 문제가 생기면 이 값만 끄고 재시작해 S3로 되돌린다.
 */
@Configuration
@EnableConfigurationProperties(CloudFrontProperties.class)
public class MediaViewUrlConfig {

    @Bean
    @ConditionalOnProperty(name = "media.view.cdn.enabled", havingValue = "true")
    public MediaViewUrlSigner cloudFrontMediaViewUrlSigner(CloudFrontProperties properties) {
        return new CloudFrontMediaViewUrlSigner(properties, Clock.systemUTC());
    }

    @Bean
    @ConditionalOnProperty(name = "media.view.cdn.enabled", havingValue = "false", matchIfMissing = true)
    public MediaViewUrlSigner s3MediaViewUrlSigner(PresignedUrlProvider presignedUrlProvider) {
        return new S3MediaViewUrlSigner(presignedUrlProvider);
    }
}
