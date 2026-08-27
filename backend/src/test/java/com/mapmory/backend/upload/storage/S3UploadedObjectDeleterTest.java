package com.mapmory.backend.upload.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectsResponse;
import software.amazon.awssdk.services.s3.model.S3Error;

class S3UploadedObjectDeleterTest {

    private final S3Client s3Client = mock(S3Client.class);
    private S3UploadedObjectDeleter deleter;

    @BeforeEach
    void setUp() {
        deleter = new S3UploadedObjectDeleter(
                s3Client,
                new S3StorageProperties("techcourse-project-2026", "ap-northeast-2", "mapmory")
        );
    }

    @Test
    void 관리하는_prefix의_객체들을_한_요청으로_삭제한다() {
        when(s3Client.deleteObjects(any(DeleteObjectsRequest.class)))
                .thenReturn(DeleteObjectsResponse.builder().build());

        deleter.deleteAll(List.of(
                "mapmory/travel-records/10/a.jpg",
                "mapmory/travel-records/10/b.jpg"
        ));

        ArgumentCaptor<DeleteObjectsRequest> captor = ArgumentCaptor.forClass(DeleteObjectsRequest.class);
        verify(s3Client).deleteObjects(captor.capture());
        assertThat(captor.getValue().bucket()).isEqualTo("techcourse-project-2026");
        assertThat(captor.getValue().delete().objects())
                .extracting(object -> object.key())
                .containsExactly(
                        "mapmory/travel-records/10/a.jpg",
                        "mapmory/travel-records/10/b.jpg"
                );
    }

    @Test
    void 관리하는_prefix_밖의_객체는_삭제하지_않는다() {
        assertThatThrownBy(() -> deleter.deleteAll(List.of("another-team/travel-records/10/a.jpg")))
                .isInstanceOf(IllegalArgumentException.class);

        verify(s3Client, never()).deleteObjects(any(DeleteObjectsRequest.class));
    }

    @Test
    void 일부_객체_삭제가_실패하면_실패로_처리한다() {
        when(s3Client.deleteObjects(any(DeleteObjectsRequest.class)))
                .thenReturn(DeleteObjectsResponse.builder()
                        .errors(S3Error.builder()
                                .key("mapmory/travel-records/10/a.jpg")
                                .code("AccessDenied")
                                .build())
                        .build());

        assertThatThrownBy(() -> deleter.deleteAll(List.of("mapmory/travel-records/10/a.jpg")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 빈_목록이면_S3를_호출하지_않는다() {
        deleter.deleteAll(List.of());

        verify(s3Client, never()).deleteObjects(any(DeleteObjectsRequest.class));
    }
}
