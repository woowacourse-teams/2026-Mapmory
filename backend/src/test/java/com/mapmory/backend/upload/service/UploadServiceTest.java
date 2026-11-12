package com.mapmory.backend.upload.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.mapmory.backend.common.exception.BusinessException;
import com.mapmory.backend.dropoff.DropOffContext;
import com.mapmory.backend.dropoff.DropOffRecorder;
import com.mapmory.backend.member.Member;
import com.mapmory.backend.member.application.ActiveMemberGuard;
import com.mapmory.backend.member.exception.MemberErrorCode;
import com.mapmory.backend.upload.PresignedUpload;
import com.mapmory.backend.upload.UploadErrorCode;
import com.mapmory.backend.upload.domain.UploadPolicy;
import com.mapmory.backend.upload.ratelimit.UploadRateLimiter;
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
                provider,
                mock(ActiveMemberGuard.class),
                mock(UploadRateLimiter.class),
                mock(DropOffRecorder.class)
        );
        List<UploadFileCommand> files = List.of(
                new UploadFileCommand("jeju-trip", "IMAGE/JPEG", 3_145_728L),
                new UploadFileCommand("map.webp", "image/webp", 1024L)
        );

        List<PresignedUpload> uploads = uploadService.createPresignedUrls(DropOffContext.NONE, member(), files);

        assertThat(uploads).hasSize(2);
        assertThat(uploads.getFirst().objectKey()).startsWith("mapmory/travel-records/10/")
                .endsWith(".jpg");
        assertThat(uploads.getFirst().presignedUrl().toString())
                .startsWith("https://upload.example/mapmory/travel-records/10/");
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

    @Test
    void 활동_중이_아닌_회원에게는_Presigned_URL을_발급하지_않는다() {
        FakePresignedUrlProvider provider = new FakePresignedUrlProvider();
        ActiveMemberGuard guard = mock(ActiveMemberGuard.class);
        willThrow(new BusinessException(MemberErrorCode.INACTIVE_MEMBER)).given(guard).require(10L);
        UploadService uploadService = new UploadService(
                new UploadPolicy(TEN_MEGABYTES, 10),
                new S3ObjectKeyGenerator(storageProperties()),
                provider,
                guard,
                mock(UploadRateLimiter.class),
                mock(DropOffRecorder.class)
        );

        assertThatThrownBy(() -> uploadService.createPresignedUrls(DropOffContext.NONE, member(),
                List.of(new UploadFileCommand("jeju.jpg", "image/jpeg", 1024L))))
                .isInstanceOf(BusinessException.class);
        assertThat(provider.requests).isEmpty();
    }

    @Test
    void 한도를_넘으면_회원을_확인하지_않고_발급하지_않는다() {
        FakePresignedUrlProvider provider = new FakePresignedUrlProvider();
        ActiveMemberGuard guard = mock(ActiveMemberGuard.class);
        UploadRateLimiter rateLimiter = mock(UploadRateLimiter.class);
        willThrow(new BusinessException(UploadErrorCode.UPLOAD_RATE_LIMITED)).given(rateLimiter).check(10L, 1);
        UploadService uploadService = new UploadService(
                new UploadPolicy(TEN_MEGABYTES, 10),
                new S3ObjectKeyGenerator(storageProperties()),
                provider,
                guard,
                rateLimiter,
                mock(DropOffRecorder.class)
        );

        assertThatThrownBy(() -> uploadService.createPresignedUrls(DropOffContext.NONE, member(),
                List.of(new UploadFileCommand("jeju.jpg", "image/jpeg", 1024L))))
                .isInstanceOf(BusinessException.class);
        assertThat(provider.requests).isEmpty();
        verifyNoInteractions(guard);
    }

    private static S3StorageProperties storageProperties() {
        return new S3StorageProperties("mapmory-test", "ap-northeast-2", "mapmory", EXPIRATION, null);
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

    @Test
    void 한도에_막히면_가장_큰_파일과_함께_막힌_지점으로_남기고_그대로_던진다() {
        UploadRateLimiter rateLimiter = mock(UploadRateLimiter.class);
        DropOffRecorder recorder = mock(DropOffRecorder.class);
        RuntimeException limited = new BusinessException(UploadErrorCode.UPLOAD_RATE_LIMITED);
        willThrow(limited).given(rateLimiter).check(anyLong(), anyInt());
        UploadService uploadService = new UploadService(
                new UploadPolicy(TEN_MEGABYTES, 10),
                new S3ObjectKeyGenerator(storageProperties()),
                new FakePresignedUrlProvider(),
                mock(ActiveMemberGuard.class),
                rateLimiter,
                recorder
        );
        DropOffContext context = new DropOffContext(10L, "okhttp/4.12.0");

        assertThatThrownBy(() -> uploadService.createPresignedUrls(context, member(), List.of(
                new UploadFileCommand("a.jpg", "image/jpeg", 1024L),
                new UploadFileCommand("b.png", "image/png", 4096L))))
                .isSameAs(limited);

        verify(recorder)
                .uploadRejected(context, 2, 4096L, "image/png", "UPLOAD_RATE_LIMITED");
    }
}
