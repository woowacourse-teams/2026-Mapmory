package com.mapmory.backend.recordmedia;

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

    public RecordMediaUrlService(MediaViewUrlSigner mediaViewUrlSigner) {
        this.mediaViewUrlSigner = mediaViewUrlSigner;
    }

    public ExpiringUrl createViewUrl(String objectKey) {
        return mediaViewUrlSigner.sign(objectKey);
    }
}
