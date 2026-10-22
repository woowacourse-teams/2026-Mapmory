package com.mapmory.backend.recordmedia;

import java.net.URI;
import java.time.Duration;

/**
 * Object Key 하나로 일정 시간만 열리는 조회 URL을 만든다.
 *
 * <p>S3 presigned GET과 CloudFront 서명 URL 중 하나가 빈으로 등록된다. (media.view.cdn.enabled)
 */
public interface MediaViewUrlSigner {

    URI sign(String objectKey, Duration expiration);
}
