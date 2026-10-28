package com.mapmory.backend.recordmedia;

import com.mapmory.backend.upload.service.port.PresignedRequest;
import com.mapmory.backend.upload.service.port.PresignedUrlProvider;

/**
 * 앱이 S3에서 바로 받는 presigned GET URL. CloudFront를 끈 환경(로컬·테스트)과 되돌릴 때 쓴다.
 */
public class S3MediaViewUrlSigner implements MediaViewUrlSigner {

    private final PresignedUrlProvider presignedUrlProvider;

    public S3MediaViewUrlSigner(PresignedUrlProvider presignedUrlProvider) {
        this.presignedUrlProvider = presignedUrlProvider;
    }

    @Override
    public ExpiringUrl sign(String objectKey) {
        PresignedRequest presigned = presignedUrlProvider.createPresignedGetUrl(objectKey);
        return ExpiringUrl.from(presigned.url(), presigned.expiration());
    }
}
