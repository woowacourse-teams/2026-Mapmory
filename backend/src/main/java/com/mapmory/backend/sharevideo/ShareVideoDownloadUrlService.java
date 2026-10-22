package com.mapmory.backend.sharevideo;

import com.mapmory.backend.upload.policy.UploadPolicyProperties;
import com.mapmory.backend.upload.storage.PresignedUrlProvider;
import java.time.Duration;
import org.springframework.stereotype.Service;

@Service
public class ShareVideoDownloadUrlService {

    private final PresignedUrlProvider presignedUrlProvider;
    private final Duration expiration;

    public ShareVideoDownloadUrlService(
            final PresignedUrlProvider presignedUrlProvider,
            final UploadPolicyProperties uploadPolicyProperties
    ) {
        this.presignedUrlProvider = presignedUrlProvider;
        this.expiration = uploadPolicyProperties.presignedUrlExpiration();
    }

    public ShareVideoDownloadUrl create(final String resultObjectKey) {
        return ShareVideoDownloadUrl.from(
                presignedUrlProvider.createPresignedGetUrl(resultObjectKey, expiration),
                expiration
        );
    }
}
