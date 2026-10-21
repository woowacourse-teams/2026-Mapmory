package com.mapmory.backend.recordmedia;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
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
 *
 * <p>이 서버의 키 접두사(upload.storage.s3.key-prefix) 아래 키만 CloudFront로 서명한다. dev 키로 운영 사진을
 * 서명하지 않게 하고, 접두사를 붙이기 전에 저장된 옛 키는 S3 presigned GET으로 낸다.
 *
 * <p>RSA 서명은 한 번에 1ms 남짓이라 목록·상세의 사진마다 하면 응답이 늦어진다. 만료 시각을 유효시간 단위로
 * 올려 맞추고, 같은 구간 안에서는 같은 키에 만든 URL을 다시 쓴다. 그래서 실제 유효시간은 설정값 이상, 두 배 미만이다.
 */
public class CloudFrontMediaViewUrlSigner implements MediaViewUrlSigner {

    private static final int MAXIMUM_CACHED_URLS = 50_000;

    private final CloudFrontUtilities cloudFrontUtilities = CloudFrontUtilities.create();
    private final Cache<SignedUrlKey, URI> signedUrls;
    private final String baseUrl;
    private final String originPrefix;
    private final String signablePrefix;
    private final String keyPairId;
    private final PrivateKey privateKey;
    private final MediaViewUrlSigner fallback;
    private final Clock clock;

    public CloudFrontMediaViewUrlSigner(
            CloudFrontProperties properties,
            String storageKeyPrefix,
            Duration expiration,
            MediaViewUrlSigner fallback,
            Clock clock
    ) {
        properties.validate();
        String originPath = strip(properties.originPath());
        if (!storageKeyPrefix.equals(originPath) && !storageKeyPrefix.startsWith(originPath + "/")) {
            throw new IllegalStateException("upload.storage.s3.key-prefix '" + storageKeyPrefix
                    + "' must be under media.view.cdn.origin-path '" + originPath + "'");
        }
        this.baseUrl = "https://" + strip(properties.domain().replaceFirst("^https?://", "")) + "/";
        this.originPrefix = originPath + "/";
        this.signablePrefix = storageKeyPrefix + "/";
        this.keyPairId = properties.keyPairId();
        this.privateKey = loadPrivateKey(properties, baseUrl);
        this.fallback = fallback;
        this.clock = clock;
        this.signedUrls = Caffeine.newBuilder()
                .maximumSize(MAXIMUM_CACHED_URLS)
                .expireAfterWrite(expiration.multipliedBy(2))
                .build();
    }

    @Override
    public URI sign(String objectKey, Duration expiration) {
        if (!objectKey.startsWith(signablePrefix)) {
            return fallback.sign(objectKey, expiration);
        }
        Instant expiresAt = roundUp(Instant.now(clock).plus(expiration), expiration);
        return signedUrls.get(new SignedUrlKey(objectKey, expiresAt), key -> signNow(key.objectKey(), expiresAt));
    }

    private URI signNow(String objectKey, Instant expiresAt) {
        CannedSignerRequest request = CannedSignerRequest.builder()
                .resourceUrl(baseUrl + objectKey.substring(originPrefix.length()))
                .privateKey(privateKey)
                .keyPairId(keyPairId)
                .expirationDate(expiresAt)
                .build();
        return URI.create(cloudFrontUtilities.getSignedUrlWithCannedPolicy(request).url());
    }

    private static Instant roundUp(Instant instant, Duration unit) {
        long unitSeconds = Math.max(1, unit.toSeconds());
        long seconds = instant.getEpochSecond() + (instant.getNano() > 0 ? 1 : 0);
        return Instant.ofEpochSecond(Math.ceilDiv(seconds, unitSeconds) * unitSeconds);
    }

    // PEM(PKCS#1·PKCS#8) 해석은 SDK에 맡긴다. 요청마다 파일을 읽지 않도록 기동 때 한 번만 읽는다.
    private static PrivateKey loadPrivateKey(CloudFrontProperties properties, String baseUrl) {
        try {
            return CannedSignerRequest.builder()
                    .resourceUrl(baseUrl)
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

    private record SignedUrlKey(String objectKey, Instant expiresAt) {
    }
}
