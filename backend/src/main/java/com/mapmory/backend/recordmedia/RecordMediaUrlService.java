package com.mapmory.backend.recordmedia;

import com.mapmory.backend.upload.storage.S3StorageProperties;
import java.time.Duration;
import org.springframework.stereotype.Service;

/**
 * Object Key 하나를 조회용 서명 URL로 바꾼다. S3 presigned GET이거나 CloudFront 서명 URL이다.
 *
 * <p>여행 일지 도메인을 알지 않는다. 어떤 미디어가 어떤 일지에 속하는지는 애그리거트 루트가
 * 판단하고, 여기서는 키를 URL로 바꾸는 일만 한다. (ADR 0017)
 */
@Service
public class RecordMediaUrlService {

    private final MediaViewUrlSigner mediaViewUrlSigner;
    private final Duration expiration;

    public RecordMediaUrlService(
            MediaViewUrlSigner mediaViewUrlSigner,
            S3StorageProperties s3StorageProperties
    ) {
        this.mediaViewUrlSigner = mediaViewUrlSigner;
        this.expiration = s3StorageProperties.signedUrlExpiration();
    }

    public ExpiringUrl createViewUrl(String objectKey) {
        return ExpiringUrl.from(
                mediaViewUrlSigner.sign(objectKey, expiration),
                expiration
        );
    }
}
