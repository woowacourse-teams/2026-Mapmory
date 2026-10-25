package com.mapmory.backend.upload.storage;

import java.net.URI;
import java.time.Duration;

public interface PresignedUrlProvider {

    /**
     * 업로드용 URL. Content-Type과 크기를 서명에 넣어 앱이 다른 값으로 올리지 못하게 한다.
     */
    PresignedRequest createPresignedPutUrl(
            String objectKey,
            String contentType,
            long contentLength,
            Duration expiration
    );

    URI createPresignedGetUrl(
            String objectKey,
            Duration expiration
    );
}
