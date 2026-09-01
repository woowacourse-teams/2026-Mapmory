package com.mapmory.backend.recordmedia;

import com.mapmory.backend.upload.policy.UploadPolicyProperties;
import com.mapmory.backend.upload.storage.PresignedUrlProvider;
import java.time.Duration;
import org.springframework.stereotype.Service;

/**
 * Object Key 하나를 조회용 presigned URL로 바꾼다.
 *
 * <p>여행 일지 도메인을 알지 않는다. 어떤 미디어가 어떤 일지에 속하는지는 애그리거트 루트가
 * 판단하고, 여기서는 키를 URL로 바꾸는 일만 한다. (ADR 0017)
 */
@Service
public class RecordMediaUrlService {

    private final PresignedUrlProvider presignedUrlProvider;
    private final Duration expiration;

    public RecordMediaUrlService(
            PresignedUrlProvider presignedUrlProvider,
            UploadPolicyProperties uploadPolicyProperties
    ) {
        this.presignedUrlProvider = presignedUrlProvider;
        this.expiration = uploadPolicyProperties.presignedUrlExpiration();
    }

    public ExpiringUrl createViewUrl(String objectKey) {
        return ExpiringUrl.from(
                presignedUrlProvider.createPresignedGetUrl(objectKey, expiration),
                expiration
        );
    }
}
