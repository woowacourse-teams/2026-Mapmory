package com.mapmory.backend.recordmedia;

import java.net.URI;
import java.security.PrivateKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import software.amazon.awssdk.services.cloudfront.CloudFrontUtilities;
import software.amazon.awssdk.services.cloudfront.model.CannedSignerRequest;

/**
 * CloudFront 서명 URL(canned policy). 서버 안에서 계산만 하고 AWS를 부르지 않는다.
 *
 * <p>CloudFront 캐시 키에 쿼리 문자열을 넣지 않아서, 서명이 매번 달라도 같은 사진은 같은 캐시를 쓴다.
 * 배포의 원본 경로가 우리 폴더라, 그 밖의 키로는 URL을 만들지 않는다.
 */
public class CloudFrontMediaViewUrlSigner implements MediaViewUrlSigner {

    private final CloudFrontUtilities cloudFrontUtilities = CloudFrontUtilities.create();
    private final String baseUrl;
    private final String keyPrefix;
    private final String keyPairId;
    private final PrivateKey privateKey;
    private final Clock clock;

    public CloudFrontMediaViewUrlSigner(CloudFrontProperties properties, Clock clock) {
        properties.validate();
        this.baseUrl = "https://" + strip(properties.domain().replaceFirst("^https?://", "")) + "/";
        this.keyPrefix = strip(properties.originPath()) + "/";
        this.keyPairId = properties.keyPairId();
        this.privateKey = loadPrivateKey(properties);
        this.clock = clock;
    }

    @Override
    public URI sign(String objectKey, Duration expiration) {
        if (!objectKey.startsWith(keyPrefix)) {
            throw new IllegalArgumentException("object key is outside the CloudFront origin path: " + objectKey);
        }
        CannedSignerRequest request = CannedSignerRequest.builder()
                .resourceUrl(baseUrl + objectKey.substring(keyPrefix.length()))
                .privateKey(privateKey)
                .keyPairId(keyPairId)
                .expirationDate(Instant.now(clock).plus(expiration))
                .build();
        return URI.create(cloudFrontUtilities.getSignedUrlWithCannedPolicy(request).url());
    }

    // PEM(PKCS#1·PKCS#8) 해석은 SDK에 맡긴다. 요청마다 파일을 읽지 않도록 기동 때 한 번만 읽는다.
    private static PrivateKey loadPrivateKey(CloudFrontProperties properties) {
        try {
            return CannedSignerRequest.builder()
                    .resourceUrl("https://" + properties.domain())
                    .privateKey(properties.privateKeyPath())
                    .keyPairId(properties.keyPairId())
                    .expirationDate(Instant.EPOCH)
                    .build()
                    .privateKey();
        } catch (Exception e) {
            throw new IllegalStateException(
                    "cannot read CloudFront private key: " + properties.privateKeyPath(), e);
        }
    }

    private static String strip(String value) {
        return value.strip()
                .replaceAll("^/+", "")
                .replaceAll("/+$", "");
    }
}
