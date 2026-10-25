package com.mapmory.backend.upload.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.mapmory.backend.member.Member;
import com.mapmory.backend.upload.PresignedUpload;
import com.mapmory.backend.upload.domain.UploadPolicy;
import com.mapmory.backend.upload.service.port.PresignedRequest;
import com.mapmory.backend.upload.service.port.PresignedUrlProvider;
import com.mapmory.backend.upload.storage.S3ObjectKeyGenerator;
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
    private static final Duration EXPIRATION = Duration.ofMinutes(5);

    @Test
    void S3_없이_Presigned_URL과_Object_Key를_발급한다() {
        FakePresignedUrlProvider provider = new FakePresignedUrlProvider();
        UploadService uploadService = new UploadService(
                new UploadPolicy(TEN_MEGABYTES, 10),
                new S3ObjectKeyGenerator(storageProperties()),
                provider
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
        assertThat(uploads.getFirst().expiration()).isEqualTo(EXPIRATION);
        assertThat(provider.requests).containsExactly(
                new PresignRequest(
                        uploads.getFirst().objectKey(),
                        "image/jpeg",
                        3_145_728L
                ),
                new PresignRequest(
                        uploads.get(1).objectKey(),
                        "image/webp",
                        1024L
                )
        );
    }

    private static S3StorageProperties storageProperties() {
        return new S3StorageProperties("mapmory-test", "ap-northeast-2", "", EXPIRATION, null);
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
                long contentLength
        ) {
            requests.add(new PresignRequest(objectKey, contentType, contentLength));
            return new PresignedRequest(URI.create("https://upload.example/" + objectKey), "PUT", EXPIRATION);
        }

        @Override
        public PresignedRequest createPresignedGetUrl(String objectKey) {
            return new PresignedRequest(URI.create("https://download.example/" + objectKey), "GET", EXPIRATION);
        }
    }

    private record PresignRequest(
            String objectKey,
            String contentType,
            long contentLength
    ) {
    }
}
