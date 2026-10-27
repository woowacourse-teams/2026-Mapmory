package com.mapmory.backend.recordmedia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CloudFrontMediaViewUrlSignerTest {

    private static final Duration FIVE_MINUTES = Duration.ofMinutes(5);
    private static final Instant NOW = Instant.parse("2026-10-21T00:01:30Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final MediaViewUrlSigner S3 = objectKey ->
            new ExpiringUrl("https://bucket.s3.example/" + objectKey + "?X-Amz-Signature=s3", 300L);

    @TempDir
    Path tempDir;

    private KeyPair keyPair;
    private Path privateKeyPath;

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        keyPair = generator.generateKeyPair();
        privateKeyPath = writePem("cloudfront-private.pem", "PRIVATE KEY", keyPair.getPrivate().getEncoded());
    }

    @Test
    void 원본_경로를_뺀_CloudFront_주소에_만료_시각과_키_ID를_붙인다() {
        MediaViewUrlSigner signer = signer("d123.cloudfront.net", "mapmory", "mapmory/dev", CLOCK);

        URI url = url(signer.sign("mapmory/dev/travel-records/15/a.jpg"));

        assertThat(url.getScheme()).isEqualTo("https");
        assertThat(url.getHost()).isEqualTo("d123.cloudfront.net");
        assertThat(url.getPath()).isEqualTo("/dev/travel-records/15/a.jpg");
        assertThat(query(url)).containsEntry("Key-Pair-Id", "KTEST");
    }

    @Test
    void 만료_시각은_유효시간_단위로_올려_맞춰_설정값_이상_두_배_미만이다() {
        MediaViewUrlSigner signer = signer("d123.cloudfront.net", "mapmory", "mapmory", CLOCK);

        URI url = url(signer.sign("mapmory/travel-records/12/b.jpg"));

        long expires = Long.parseLong(query(url).get("Expires"));
        assertThat(expires).isEqualTo(Instant.parse("2026-10-21T00:10:00Z").getEpochSecond());
        assertThat(expires - NOW.getEpochSecond()).isBetween(300L, 599L);
    }

    @Test
    void 만료까지_남은_시간은_설정한_유효시간으로_알려준다() {
        MediaViewUrlSigner signer = signer("d123.cloudfront.net", "mapmory", "mapmory", CLOCK);

        ExpiringUrl url = signer.sign("mapmory/travel-records/12/b.jpg");

        assertThat(url.expiresIn()).isEqualTo(FIVE_MINUTES.toSeconds());
    }

    @Test
    void 서명은_짝이_되는_공개_키로_검증된다() throws Exception {
        MediaViewUrlSigner signer = signer("d123.cloudfront.net", "mapmory", "mapmory", CLOCK);

        URI url = url(signer.sign("mapmory/travel-records/12/b.jpg"));

        assertThat(verifies(url, "https://d123.cloudfront.net/travel-records/12/b.jpg")).isTrue();
    }

    @Test
    void openssl_genrsa가_만드는_PKCS1_PEM도_읽는다() throws Exception {
        privateKeyPath = writePem("pkcs1.pem", "RSA PRIVATE KEY", pkcs1(keyPair.getPrivate().getEncoded()));
        MediaViewUrlSigner signer = signer("d123.cloudfront.net", "mapmory", "mapmory", CLOCK);

        URI url = url(signer.sign("mapmory/travel-records/12/b.jpg"));

        assertThat(verifies(url, "https://d123.cloudfront.net/travel-records/12/b.jpg")).isTrue();
    }

    @Test
    void 같은_구간_안에서는_같은_키에_같은_URL을_다시_쓴다() {
        MutableClock clock = new MutableClock(NOW);
        MediaViewUrlSigner signer = signer("d123.cloudfront.net", "mapmory", "mapmory", clock);

        URI first = url(signer.sign("mapmory/travel-records/12/b.jpg"));
        clock.now = NOW.plusSeconds(60);
        URI sameWindow = url(signer.sign("mapmory/travel-records/12/b.jpg"));
        clock.now = NOW.plus(FIVE_MINUTES);
        URI nextWindow = url(signer.sign("mapmory/travel-records/12/b.jpg"));

        assertThat(sameWindow).isEqualTo(first);
        assertThat(nextWindow).isNotEqualTo(first);
    }

    @Test
    void 도메인과_원본_경로의_앞뒤_슬래시와_스킴은_무시한다() {
        MediaViewUrlSigner signer = signer("https://d123.cloudfront.net/", "/mapmory/", "mapmory", CLOCK);

        URI url = url(signer.sign("mapmory/travel-records/12/b.jpg"));

        assertThat(url.getHost()).isEqualTo("d123.cloudfront.net");
        assertThat(url.getPath()).isEqualTo("/travel-records/12/b.jpg");
    }

    @Test
    void 이_서버의_키_접두사_밖의_키는_S3_presigned_GET으로_낸다() {
        MediaViewUrlSigner devSigner = signer("d123.cloudfront.net", "mapmory", "mapmory/dev", CLOCK);

        URI prodKey = url(devSigner.sign("mapmory/travel-records/12/b.jpg"));
        URI legacyKey = url(devSigner.sign("travel-records/12/b.jpg"));

        assertThat(prodKey.getHost()).isEqualTo("bucket.s3.example");
        assertThat(legacyKey.getHost()).isEqualTo("bucket.s3.example");
    }

    @Test
    void 키_접두사가_원본_경로_밖이면_만들_때_실패한다() {
        assertThatThrownBy(() -> signer("d123.cloudfront.net", "mapmory", "", CLOCK))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("key-prefix");
        assertThatThrownBy(() -> signer("d123.cloudfront.net", "mapmory", "mapmory-other", CLOCK))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("key-prefix");
    }

    @Test
    void 켜져_있는데_값이_빠지면_만들_때_실패한다() {
        CloudFrontProperties missingKeyPairId = new CloudFrontProperties(
                true, "d123.cloudfront.net", "mapmory", " ", privateKeyPath
        );
        CloudFrontProperties missingPrivateKey = new CloudFrontProperties(
                true, "d123.cloudfront.net", "mapmory", "KTEST", null
        );

        assertThatThrownBy(() -> new CloudFrontMediaViewUrlSigner(missingKeyPairId, "mapmory", FIVE_MINUTES, S3, CLOCK))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("key-pair-id");
        assertThatThrownBy(() -> new CloudFrontMediaViewUrlSigner(missingPrivateKey, "mapmory", FIVE_MINUTES, S3, CLOCK))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("private-key-path");
    }

    private static URI url(ExpiringUrl expiringUrl) {
        return URI.create(expiringUrl.url());
    }

    private MediaViewUrlSigner signer(String domain, String originPath, String keyPrefix, Clock clock) {
        return new CloudFrontMediaViewUrlSigner(
                new CloudFrontProperties(true, domain, originPath, "KTEST", privateKeyPath),
                keyPrefix,
                FIVE_MINUTES,
                S3,
                clock
        );
    }

    private boolean verifies(URI url, String resource) throws Exception {
        Map<String, String> query = query(url);
        String policy = "{\"Statement\":[{\"Resource\":\"" + resource
                + "\",\"Condition\":{\"DateLessThan\":{\"AWS:EpochTime\":" + query.get("Expires") + "}}}]}";
        byte[] signature = Base64.getDecoder().decode(query.get("Signature")
                .replace('-', '+').replace('_', '=').replace('~', '/'));
        Signature verifier = Signature.getInstance("SHA1withRSA");
        verifier.initVerify(keyPair.getPublic());
        verifier.update(policy.getBytes(StandardCharsets.UTF_8));
        return verifier.verify(signature);
    }

    private Path writePem(String fileName, String type, byte[] der) throws Exception {
        Path path = tempDir.resolve(fileName);
        String pem = "-----BEGIN " + type + "-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII)).encodeToString(der)
                + "\n-----END " + type + "-----\n";
        Files.writeString(path, pem);
        return path;
    }

    // PKCS#8 PrivateKeyInfo의 마지막 OCTET STRING 안이 PKCS#1 RSAPrivateKey다.
    private static byte[] pkcs1(byte[] pkcs8) {
        int index = pkcs8.length;
        for (int i = 0; i < pkcs8.length - 4; i++) {
            if (pkcs8[i] == 0x04 && (pkcs8[i + 1] & 0xff) == 0x82 && pkcs8[i + 4] == 0x30) {
                index = i + 4;
                break;
            }
        }
        return Arrays.copyOfRange(pkcs8, index, pkcs8.length);
    }

    private static Map<String, String> query(URI url) {
        Map<String, String> values = new LinkedHashMap<>();
        for (String pair : url.getRawQuery().split("&")) {
            int separator = pair.indexOf('=');
            values.put(pair.substring(0, separator), pair.substring(separator + 1));
        }
        return values;
    }

    private static final class MutableClock extends Clock {

        private Instant now;

        private MutableClock(Instant now) {
            this.now = now;
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
