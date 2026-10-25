package com.mapmory.backend.recordmedia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.mapmory.backend.upload.storage.PresignedUrlProvider;
import com.mapmory.backend.upload.storage.S3StorageProperties;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.time.Duration;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MediaViewUrlConfigTest {

    private final MediaViewUrlConfig config = new MediaViewUrlConfig();
    private final S3StorageProperties s3StorageProperties = new S3StorageProperties(
            "bucket", "ap-northeast-2", "mapmory", Duration.ofMinutes(5),
            new S3StorageProperties.Client(Duration.ofSeconds(2), Duration.ofSeconds(6), 3)
    );

    @TempDir
    Path tempDir;

    @Test
    void 꺼져_있으면_S3_presigned_GET을_쓴다() {
        CloudFrontProperties off = new CloudFrontProperties(false, null, "mapmory", null, null);

        MediaViewUrlSigner signer = config.mediaViewUrlSigner(
                off, s3StorageProperties, mock(PresignedUrlProvider.class));

        assertThat(signer).isInstanceOf(S3MediaViewUrlSigner.class);
    }

    @Test
    void 켜져_있으면_CloudFront_서명_URL을_쓴다() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        Path key = tempDir.resolve("key.pem");
        Files.writeString(key, "-----BEGIN PRIVATE KEY-----\n"
                + Base64.getMimeEncoder().encodeToString(generator.generateKeyPair().getPrivate().getEncoded())
                + "\n-----END PRIVATE KEY-----\n");
        CloudFrontProperties on = new CloudFrontProperties(true, "d123.cloudfront.net", "mapmory", "KTEST", key);

        MediaViewUrlSigner signer = config.mediaViewUrlSigner(
                on, s3StorageProperties, mock(PresignedUrlProvider.class));

        assertThat(signer).isInstanceOf(CloudFrontMediaViewUrlSigner.class);
    }
}
