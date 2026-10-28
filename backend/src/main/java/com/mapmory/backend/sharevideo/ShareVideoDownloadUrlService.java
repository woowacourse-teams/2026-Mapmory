package com.mapmory.backend.sharevideo;

import com.mapmory.backend.upload.service.port.PresignedRequest;
import com.mapmory.backend.upload.service.port.PresignedUrlProvider;
import org.springframework.stereotype.Service;

@Service
public class ShareVideoDownloadUrlService {

    private final PresignedUrlProvider presignedUrlProvider;

    public ShareVideoDownloadUrlService(final PresignedUrlProvider presignedUrlProvider) {
        this.presignedUrlProvider = presignedUrlProvider;
    }

    public ShareVideoDownloadUrl create(final String resultObjectKey) {
        PresignedRequest presigned = presignedUrlProvider.createPresignedGetUrl(resultObjectKey);
        return ShareVideoDownloadUrl.from(presigned.url(), presigned.expiration());
    }
}
