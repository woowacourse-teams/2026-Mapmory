package com.mapmory.backend.recordmedia;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 사진 조회를 CloudFront로 보낼 때 필요한 값.
 *
 * <p>originPath는 배포의 원본 경로(공용 버킷의 우리 폴더)다. Object Key가 이 값으로 시작하고,
 * CloudFront 주소에서는 이 부분을 뺀다. 예: mapmory/dev/travel-records/1/a.jpg → /dev/travel-records/1/a.jpg
 *
 * <p>keyPairId는 CloudFront 퍼블릭 키 ID다. 서버마다 다른 키를 쓴다(dev/prod).
 * 비밀 키는 여러 줄이라 환경변수 대신 파일 경로로 받는다.
 */
@ConfigurationProperties(prefix = "media.view.cdn")
public record CloudFrontProperties(
        boolean enabled,
        String domain,
        String originPath,
        String keyPairId,
        Path privateKeyPath
) {

    public void validate() {
        requireText(domain, "media.view.cdn.domain");
        requireText(originPath, "media.view.cdn.origin-path");
        requireText(keyPairId, "media.view.cdn.key-pair-id");
        if (privateKeyPath == null) {
            throw new IllegalStateException("media.view.cdn.private-key-path must be set when CloudFront is enabled");
        }
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " must be set when CloudFront is enabled");
        }
    }
}
