package com.mapmory.backend.upload.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.mapmory.backend.member.Member;
import com.mapmory.backend.upload.PresignedUpload;
import com.mapmory.backend.upload.domain.UploadPolicy;
import com.mapmory.backend.upload.storage.ObjectKeyGenerator;
import com.mapmory.backend.upload.storage.PresignedRequest;
import com.mapmory.backend.upload.storage.PresignedUrlProvider;
import com.mapmory.backend.upload.storage.S3StorageProperties;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.unit.DataSize;

class UploadServiceTest {

    private static final long TEN_MEGABYTES = DataSize.ofMegabytes(10).toBytes();

    @Test
    void S3_없이_Presigned_URL과_Object_Key를_발급한다() {
        S3StorageProperties storageProperties = storageProperties();
        FakePresignedUrlProvider provider = new FakePresignedUrlProvider();
        UploadService uploadService = new UploadService(
                new UploadPolicy(TEN_MEGABYTES, 10),
                new ObjectKeyGenerator(storageProperties),
                provider,
                storageProperties
        );
        List<UploadFileCommand> files = List.of(
                new UploadFileCommand("jeju-trip", "IMAGE/JPEG", 3_145_728L),
                new UploadFileCommand("map.webp", "image/webp", 1024L)
        );

        List<PresignedUpload> uploads = uploadService.createPresignedUrls(member(), files);

        assertThat(uploads).hasSize(2);
        assertThat(uploads.getFirst().objectKey()).startsWith("travel-records/10/")
                .endsWith(".jpg");
        assertThat(uploads.getFirst().presignedUrl().toString())
                .startsWith("https://upload.example/travel-records/10/");
        assertThat(uploads.getFirst().method()).isEqualTo("PUT");
        assertThat(uploads.getFirst().contentType()).isEqualTo("image/jpeg");
        assertThat(uploads.getFirst().expiration()).isEqualTo(java.time.Duration.ofSeconds(300L));
        assertThat(provider.requests).containsExactly(
                new PresignRequest(
                        uploads.getFirst().objectKey(),
                        "image/jpeg",
                        3_145_728L,
                        Duration.ofMinutes(5)
                ),
                new PresignRequest(
                        uploads.get(1).objectKey(),
                        "image/webp",
                        1024L,
                        Duration.ofMinutes(5)
                )
        );
    }

    private static S3StorageProperties storageProperties() {
        return new S3StorageProperties("mapmory-test", "ap-northeast-2", "", Duration.ofMinutes(5), null);
    }

    private static Member member() {
        Member member = Member.of("테스터", UUID.randomUUID());
        ReflectionTestUtils.setField(member, "id", 10L);
        return member;
    }

    private static class FakePresignedUrlProvider implements PresignedUrlProvider {

        private final List<PresignRequest> requests = new ArrayList<>();

        @Override
        public PresignedRequest createPresignedPutUrl(
                String objectKey,
                String contentType,
                long contentLength,
                Duration expiration
        ) {
            requests.add(new PresignRequest(objectKey, contentType, contentLength, expiration));
            return new PresignedRequest(URI.create("https://upload.example/" + objectKey), "PUT");
        }

        @Override
        public URI createPresignedGetUrl(String objectKey, Duration expiration) {
            return URI.create("https://download.example/" + objectKey);
        }
    }

    private record PresignRequest(
            String objectKey,
            String contentType,
            long contentLength,
            Duration expiration
    ) {
    }
}
