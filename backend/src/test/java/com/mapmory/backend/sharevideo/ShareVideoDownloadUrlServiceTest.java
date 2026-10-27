package com.mapmory.backend.sharevideo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mapmory.backend.upload.service.port.PresignedRequest;
import com.mapmory.backend.upload.service.port.PresignedUrlProvider;
import java.net.URI;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ShareVideoDownloadUrlServiceTest {

    private static final Duration EXPIRATION = Duration.ofMinutes(5);

    private final PresignedUrlProvider presignedUrlProvider = mock(PresignedUrlProvider.class);
    private ShareVideoDownloadUrlService downloadUrlService;

    @BeforeEach
    void setUp() {
        downloadUrlService = new ShareVideoDownloadUrlService(presignedUrlProvider);
    }

    @Test
    void Object_Key로_다운로드_URL을_생성한다() {
        String objectKey = "share-videos/15/result.mp4";
        URI presignedUrl = URI.create("https://download.example/share-videos/15/result.mp4");
        when(presignedUrlProvider.createPresignedGetUrl(objectKey))
                .thenReturn(new PresignedRequest(presignedUrl, "GET", EXPIRATION));

        ShareVideoDownloadUrl result = downloadUrlService.create(objectKey);

        assertThat(result).isEqualTo(new ShareVideoDownloadUrl(presignedUrl.toString(), 300L));
        verify(presignedUrlProvider).createPresignedGetUrl(objectKey);
    }

    @Test
    void 다운로드_URL은_빈_URL을_허용하지_않는다() {
        assertThatThrownBy(() -> new ShareVideoDownloadUrl(" ", 300L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 다운로드_URL은_유효하지_않은_만료_시간을_허용하지_않는다() {
        assertThatThrownBy(() -> new ShareVideoDownloadUrl(
                "https://download.example/video.mp4",
                0L
        )).isInstanceOf(IllegalArgumentException.class);
    }
}
