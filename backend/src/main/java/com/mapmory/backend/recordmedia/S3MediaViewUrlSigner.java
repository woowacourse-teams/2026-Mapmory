package com.mapmory.backend.recordmedia;

import com.mapmory.backend.upload.storage.PresignedUrlProvider;
import java.net.URI;
import java.time.Duration;

/**
 * 앱이 S3에서 바로 받는 presigned GET URL. CloudFront를 끈 환경(로컬·테스트)과 되돌릴 때 쓴다.
 */
public class S3MediaViewUrlSigner implements MediaViewUrlSigner {

    private final PresignedUrlProvider presignedUrlProvider;

    public S3MediaViewUrlSigner(PresignedUrlProvider presignedUrlProvider) {
        this.presignedUrlProvider = presignedUrlProvider;
    }

    @Override
    public URI sign(String objectKey, Duration expiration) {
        return presignedUrlProvider.createPresignedGetUrl(objectKey, expiration);
    }
}
