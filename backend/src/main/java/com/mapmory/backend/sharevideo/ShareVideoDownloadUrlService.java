package com.mapmory.backend.sharevideo;

import com.mapmory.backend.upload.storage.S3StorageProperties;
import com.mapmory.backend.upload.storage.PresignedUrlProvider;
import java.time.Duration;
import org.springframework.stereotype.Service;

@Service
public class ShareVideoDownloadUrlService {

    private final PresignedUrlProvider presignedUrlProvider;
    private final Duration expiration;

    public ShareVideoDownloadUrlService(
            final PresignedUrlProvider presignedUrlProvider,
            final S3StorageProperties s3StorageProperties
    ) {
        this.presignedUrlProvider = presignedUrlProvider;
        this.expiration = s3StorageProperties.signedUrlExpiration();
    }

    public ShareVideoDownloadUrl create(final String resultObjectKey) {
        return ShareVideoDownloadUrl.from(
                presignedUrlProvider.createPresignedGetUrl(resultObjectKey, expiration),
                expiration
        );
    }
}
