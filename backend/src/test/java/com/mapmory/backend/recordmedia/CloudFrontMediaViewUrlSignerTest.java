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
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CloudFrontMediaViewUrlSignerTest {

    private static final Instant NOW = Instant.parse("2026-10-21T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @TempDir
    Path tempDir;

    private KeyPair keyPair;
    private Path privateKeyPath;

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        keyPair = generator.generateKeyPair();
        privateKeyPath = tempDir.resolve("cloudfront-private.pem");
        String pem = "-----BEGIN PRIVATE KEY-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII))
                .encodeToString(keyPair.getPrivate().getEncoded())
                + "\n-----END PRIVATE KEY-----\n";
        Files.writeString(privateKeyPath, pem);
    }

    @Test
    void 원본_경로를_뺀_CloudFront_주소에_만료_시각과_키_ID를_붙인다() {
        CloudFrontMediaViewUrlSigner signer = signer("d123.cloudfront.net", "mapmory");

        URI url = signer.sign("mapmory/dev/travel-records/15/a.jpg", Duration.ofMinutes(5));

        assertThat(url.getScheme()).isEqualTo("https");
        assertThat(url.getHost()).isEqualTo("d123.cloudfront.net");
        assertThat(url.getPath()).isEqualTo("/dev/travel-records/15/a.jpg");
        Map<String, String> query = query(url);
        assertThat(query).containsEntry("Expires", String.valueOf(NOW.plusSeconds(300).getEpochSecond()))
                .containsEntry("Key-Pair-Id", "KTEST");
    }

    @Test
    void 서명은_짝이_되는_공개_키로_검증된다() throws Exception {
        CloudFrontMediaViewUrlSigner signer = signer("d123.cloudfront.net", "mapmory");

        URI url = signer.sign("mapmory/travel-records/12/b.jpg", Duration.ofMinutes(5));

        Map<String, String> query = query(url);
        String resource = "https://d123.cloudfront.net/travel-records/12/b.jpg";
        String policy = "{\"Statement\":[{\"Resource\":\"" + resource
                + "\",\"Condition\":{\"DateLessThan\":{\"AWS:EpochTime\":" + query.get("Expires") + "}}}]}";
        byte[] signature = Base64.getDecoder().decode(query.get("Signature")
                .replace('-', '+').replace('_', '=').replace('~', '/'));
        Signature verifier = Signature.getInstance("SHA1withRSA");
        verifier.initVerify(keyPair.getPublic());
        verifier.update(policy.getBytes(StandardCharsets.UTF_8));

        assertThat(verifier.verify(signature)).isTrue();
    }

    @Test
    void 도메인과_원본_경로의_앞뒤_슬래시와_스킴은_무시한다() {
        CloudFrontMediaViewUrlSigner signer = signer("https://d123.cloudfront.net/", "/mapmory/");

        URI url = signer.sign("mapmory/travel-records/12/b.jpg", Duration.ofMinutes(5));

        assertThat(url.getHost()).isEqualTo("d123.cloudfront.net");
        assertThat(url.getPath()).isEqualTo("/travel-records/12/b.jpg");
    }

    @Test
    void 원본_경로_밖의_키로는_URL을_만들지_않는다() {
        CloudFrontMediaViewUrlSigner signer = signer("d123.cloudfront.net", "mapmory");

        assertThatThrownBy(() -> signer.sign("other-team/travel-records/1/a.jpg", Duration.ofMinutes(5)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> signer.sign("mapmory-other/a.jpg", Duration.ofMinutes(5)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 켜져_있는데_값이_빠지면_만들_때_실패한다() {
        CloudFrontProperties missingKeyPairId = new CloudFrontProperties(
                true, "d123.cloudfront.net", "mapmory", " ", privateKeyPath
        );
        CloudFrontProperties missingPrivateKey = new CloudFrontProperties(
                true, "d123.cloudfront.net", "mapmory", "KTEST", null
        );

        assertThatThrownBy(() -> new CloudFrontMediaViewUrlSigner(missingKeyPairId, CLOCK))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("key-pair-id");
        assertThatThrownBy(() -> new CloudFrontMediaViewUrlSigner(missingPrivateKey, CLOCK))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("private-key-path");
    }

    private CloudFrontMediaViewUrlSigner signer(String domain, String originPath) {
        return new CloudFrontMediaViewUrlSigner(
                new CloudFrontProperties(true, domain, originPath, "KTEST", privateKeyPath),
                CLOCK
        );
    }

    private static Map<String, String> query(URI url) {
        Map<String, String> values = new LinkedHashMap<>();
        for (String pair : url.getRawQuery().split("&")) {
            int separator = pair.indexOf('=');
            values.put(pair.substring(0, separator), pair.substring(separator + 1));
        }
        return values;
    }
}
