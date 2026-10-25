package com.mapmory.backend.upload.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mapmory.backend.upload.service.port.PresignedRequest;
import java.net.URI;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.http.SdkHttpFullRequest;
import software.amazon.awssdk.http.SdkHttpMethod;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

class S3PresignedUrlProviderTest {

    private final S3Presigner s3Presigner = mock(S3Presigner.class);
    private S3PresignedUrlProvider provider;

    @BeforeEach
    void setUp() {
        provider = new S3PresignedUrlProvider(
                s3Presigner,
                new S3StorageProperties("mapmory-test", "ap-northeast-2", "mapmory", Duration.ofMinutes(5), null)
        );
    }

    @Test
    void Presigned_URL을_생성한다() throws Exception {
        PresignedPutObjectRequest result = mock(PresignedPutObjectRequest.class);
        when(result.url()).thenReturn(URI.create("https://upload.example/object-key").toURL());
        when(result.httpRequest()).thenReturn(SdkHttpFullRequest.builder()
                .method(SdkHttpMethod.PUT)
                .uri(URI.create("https://upload.example/object-key"))
                .build());
        when(s3Presigner.presignPutObject(any(PutObjectPresignRequest.class))).thenReturn(result);

        PresignedRequest presigned = provider.createPresignedPutUrl(
                "object-key",
                "image/jpeg",
                1024L
        );

        assertThat(presigned.url()).isEqualTo(URI.create("https://upload.example/object-key"));
        assertThat(presigned.method()).isEqualTo("PUT");
        assertThat(presigned.expiration()).isEqualTo(Duration.ofMinutes(5));
        ArgumentCaptor<PutObjectPresignRequest> captor = ArgumentCaptor.forClass(PutObjectPresignRequest.class);
        verify(s3Presigner).presignPutObject(captor.capture());
        assertThat(captor.getValue().signatureDuration()).isEqualTo(Duration.ofMinutes(5));
    }

    @Test
    void Presigned_URL_생성_실패_예외를_전파한다() {
        when(s3Presigner.presignPutObject(any(PutObjectPresignRequest.class)))
                .thenThrow(new IllegalStateException("s3 failure"));

        assertThatThrownBy(() -> provider.createPresignedPutUrl(
                "object-key",
                "image/jpeg",
                1024L
        )).isInstanceOf(IllegalStateException.class)
                .hasMessage("s3 failure");
    }

    @Test
    void Presigned_GET_URL을_생성한다() throws Exception {
        PresignedGetObjectRequest result = mock(PresignedGetObjectRequest.class);
        when(result.url()).thenReturn(URI.create("https://download.example/object-key").toURL());
        when(result.httpRequest()).thenReturn(SdkHttpFullRequest.builder()
                .method(SdkHttpMethod.GET)
                .uri(URI.create("https://download.example/object-key"))
                .build());
        when(s3Presigner.presignGetObject(any(GetObjectPresignRequest.class))).thenReturn(result);

        PresignedRequest presigned = provider.createPresignedGetUrl("object-key");

        assertThat(presigned.url()).isEqualTo(URI.create("https://download.example/object-key"));
        assertThat(presigned.method()).isEqualTo("GET");
        assertThat(presigned.expiration()).isEqualTo(Duration.ofMinutes(5));
        ArgumentCaptor<GetObjectPresignRequest> captor = ArgumentCaptor.forClass(GetObjectPresignRequest.class);
        verify(s3Presigner).presignGetObject(captor.capture());
        assertThat(captor.getValue().signatureDuration()).isEqualTo(Duration.ofMinutes(5));
        assertThat(captor.getValue().getObjectRequest().bucket()).isEqualTo("mapmory-test");
        assertThat(captor.getValue().getObjectRequest().key()).isEqualTo("object-key");
    }

    @Test
    void Presigned_GET_URL_생성_실패_예외를_전파한다() {
        when(s3Presigner.presignGetObject(any(GetObjectPresignRequest.class)))
                .thenThrow(new IllegalStateException("s3 failure"));

        assertThatThrownBy(() -> provider.createPresignedGetUrl("object-key"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("s3 failure");
    }
}
