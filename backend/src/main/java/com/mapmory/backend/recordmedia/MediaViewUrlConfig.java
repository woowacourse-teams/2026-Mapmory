package com.mapmory.backend.recordmedia;

import com.mapmory.backend.upload.storage.PresignedUrlProvider;
import com.mapmory.backend.upload.storage.S3StorageProperties;
import java.time.Clock;
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
    public MediaViewUrlSigner mediaViewUrlSigner(
            CloudFrontProperties cloudFrontProperties,
            S3StorageProperties s3StorageProperties,
            PresignedUrlProvider presignedUrlProvider
    ) {
        MediaViewUrlSigner s3 = new S3MediaViewUrlSigner(presignedUrlProvider);
        if (!cloudFrontProperties.enabled()) {
            return s3;
        }
        return new CloudFrontMediaViewUrlSigner(
                cloudFrontProperties,
                s3StorageProperties.normalizedKeyPrefix(),
                s3StorageProperties.signedUrlExpiration(),
                s3,
                Clock.systemUTC()
        );
    }
}
