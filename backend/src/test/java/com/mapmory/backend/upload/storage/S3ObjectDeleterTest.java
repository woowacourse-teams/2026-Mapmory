package com.mapmory.backend.upload.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteMarkerEntry;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectsResponse;
import software.amazon.awssdk.services.s3.model.ListObjectVersionsRequest;
import software.amazon.awssdk.services.s3.model.ListObjectVersionsResponse;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import software.amazon.awssdk.services.s3.model.ObjectVersion;
import software.amazon.awssdk.services.s3.model.S3Error;

class S3ObjectDeleterTest {

    private static final String BUCKET = "techcourse-project-2026";
    private static final Duration DELETE_ATTEMPT = Duration.ofSeconds(10);
    private static final Duration DELETE_TOTAL = Duration.ofSeconds(30);

    private final S3Client s3Client = mock(S3Client.class);
    private final S3ObjectDeleter deleter = new S3ObjectDeleter(s3Client, new S3StorageProperties(
            BUCKET, "ap-northeast-2", "mapmory", Duration.ofMinutes(5), new S3StorageProperties.Client(
            Duration.ofSeconds(1), Duration.ofSeconds(3), 3,
            Duration.ofSeconds(10), Duration.ofSeconds(30), DELETE_ATTEMPT, DELETE_TOTAL)));

    @BeforeEach
    void setUp() {
        // 키마다 버전이 하나 있다.
        when(s3Client.listObjectVersions(any(ListObjectVersionsRequest.class))).thenAnswer(invocation -> {
            ListObjectVersionsRequest request = invocation.getArgument(0);
            return ListObjectVersionsResponse.builder()
                    .versions(version(request.prefix(), "v1"))
                    .build();
        });
        when(s3Client.deleteObjects(any(DeleteObjectsRequest.class))).thenReturn(success());
    }

    /**
     * 버킷은 버전 관리가 켜져 있다. 버전 없이 지우면 삭제 마커만 붙고 원본이 남는다.
     */
    @Test
    void 버전을_지정해_설정된_버킷에서_영구히_지운다() {
        List<String> failed = deleter.delete(List.of(key(1), key(2)));

        assertThat(failed).isEmpty();
        ArgumentCaptor<DeleteObjectsRequest> request = ArgumentCaptor.forClass(DeleteObjectsRequest.class);
        verify(s3Client).deleteObjects(request.capture());
        assertThat(request.getValue().bucket()).isEqualTo(BUCKET);
        assertThat(request.getValue().delete().objects()).containsExactly(id(key(1), "v1"), id(key(2), "v1"));
        assertThat(request.getValue().overrideConfiguration().orElseThrow().apiCallAttemptTimeout())
                .contains(DELETE_ATTEMPT);
        assertThat(request.getValue().overrideConfiguration().orElseThrow().apiCallTimeout())
                .contains(DELETE_TOTAL);
    }

    @Test
    void 키의_모든_버전과_삭제_마커를_지운다() {
        when(s3Client.listObjectVersions(any(ListObjectVersionsRequest.class))).thenReturn(
                ListObjectVersionsResponse.builder()
                        .versions(version(key(1), "v1"), version(key(1), "v2"))
                        .deleteMarkers(marker(key(1), "m1"))
                        .build());

        assertThat(deleter.delete(List.of(key(1)))).isEmpty();

        assertThat(deletedIds()).containsExactlyInAnyOrder(id(key(1), "v1"), id(key(1), "v2"), id(key(1), "m1"));
    }

    /**
     * 버전 조회는 prefix로 찾으므로 a.jpg를 찾으면 a.jpg.bak도 나온다.
     */
    @Test
    void 키가_정확히_같은_버전만_지운다() {
        when(s3Client.listObjectVersions(any(ListObjectVersionsRequest.class))).thenReturn(
                ListObjectVersionsResponse.builder()
                        .versions(version(key(1), "v1"), version(key(1) + ".bak", "other"))
                        .deleteMarkers(marker(key(1) + ".bak", "other-marker"))
                        .build());

        deleter.delete(List.of(key(1)));

        assertThat(deletedIds()).containsExactly(id(key(1), "v1"));
    }

    @Test
    void 버전_목록이_여러_쪽이면_끝까지_읽는다() {
        when(s3Client.listObjectVersions(any(ListObjectVersionsRequest.class)))
                .thenReturn(ListObjectVersionsResponse.builder()
                        .versions(version(key(1), "v1"))
                        .isTruncated(true)
                        .nextKeyMarker(key(1))
                        .nextVersionIdMarker("v1")
                        .build())
                .thenReturn(ListObjectVersionsResponse.builder()
                        .versions(version(key(1), "v2"))
                        .build());

        deleter.delete(List.of(key(1)));

        assertThat(deletedIds()).containsExactly(id(key(1), "v1"), id(key(1), "v2"));
        verify(s3Client).listObjectVersions(argThat((ListObjectVersionsRequest request) ->
                key(1).equals(request.keyMarker()) && "v1".equals(request.versionIdMarker())));
    }

    @Test
    void 이미_없는_키는_지운_것으로_본다() {
        when(s3Client.listObjectVersions(any(ListObjectVersionsRequest.class)))
                .thenReturn(ListObjectVersionsResponse.builder().build());

        assertThat(deleter.delete(List.of(key(1)))).isEmpty();

        verify(s3Client, never()).deleteObjects(any(DeleteObjectsRequest.class));
    }

    @Test
    void 키가_없으면_S3를_부르지_않는다() {
        assertThat(deleter.delete(List.of())).isEmpty();

        verify(s3Client, never()).listObjectVersions(any(ListObjectVersionsRequest.class));
        verify(s3Client, never()).deleteObjects(any(DeleteObjectsRequest.class));
    }

    @Test
    void 천_개가_넘으면_천_개씩_나눠_지운다() {
        deleter.delete(IntStream.rangeClosed(1, 1001).mapToObj(this::key).toList());

        ArgumentCaptor<DeleteObjectsRequest> request = ArgumentCaptor.forClass(DeleteObjectsRequest.class);
        verify(s3Client, times(2)).deleteObjects(request.capture());
        assertThat(request.getAllValues()).extracting(value -> value.delete().objects().size())
                .containsExactly(1000, 1);
    }

    @Test
    void 같은_키는_한_번만_지운다() {
        deleter.delete(List.of(key(1), key(1)));

        assertThat(deletedIds()).containsExactly(id(key(1), "v1"));
    }

    @Test
    void 일부_버전이_실패하면_그_키를_한_번만_돌려준다() {
        when(s3Client.listObjectVersions(any(ListObjectVersionsRequest.class))).thenReturn(
                ListObjectVersionsResponse.builder()
                        .versions(version(key(2), "v1"), version(key(2), "v2"))
                        .build());
        when(s3Client.deleteObjects(any(DeleteObjectsRequest.class))).thenReturn(DeleteObjectsResponse.builder()
                .errors(error(key(2), "v1"), error(key(2), "v2"))
                .build());

        assertThat(deleter.delete(List.of(key(2)))).containsExactly(key(2));
    }

    @Test
    void 버전을_조회하지_못한_키를_돌려주고_나머지는_지운다() {
        when(s3Client.listObjectVersions(argThat((ListObjectVersionsRequest request) ->
                request != null && key(1).equals(request.prefix()))))
                .thenThrow(SdkClientException.create("timeout"));

        assertThat(deleter.delete(List.of(key(1), key(2)))).containsExactly(key(1));

        assertThat(deletedIds()).containsExactly(id(key(2), "v1"));
    }

    @Test
    void 호출이_실패한_묶음의_키를_돌려주고_다음_묶음은_지운다() {
        when(s3Client.deleteObjects(any(DeleteObjectsRequest.class)))
                .thenThrow(SdkClientException.create("timeout"))
                .thenReturn(success());

        List<String> failed = deleter.delete(IntStream.rangeClosed(1, 1001).mapToObj(this::key).toList());

        assertThat(failed).hasSize(1000).contains(key(1), key(1000)).doesNotContain(key(1001));
        verify(s3Client, times(2)).deleteObjects(any(DeleteObjectsRequest.class));
    }

    /**
     * 공용 버킷이라 다른 팀 객체를 지우면 되돌릴 수 없다. prefix가 같은 글자로 시작하는 다른 경로도 막는다.
     */
    @Test
    void 우리_prefix_밖의_키가_하나라도_있으면_아무것도_지우지_않는다() {
        for (String foreign : List.of("other-team/a.jpg", "mapmory-other/a.jpg", "a.jpg")) {
            assertThatThrownBy(() -> deleter.delete(List.of(key(1), foreign)))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        verifyNoInteractions(s3Client);
    }

    private String key(int number) {
        return "mapmory/travel-records/1/" + number + ".jpg";
    }

    private ObjectVersion version(String key, String versionId) {
        return ObjectVersion.builder().key(key).versionId(versionId).build();
    }

    private DeleteMarkerEntry marker(String key, String versionId) {
        return DeleteMarkerEntry.builder().key(key).versionId(versionId).build();
    }

    private ObjectIdentifier id(String key, String versionId) {
        return ObjectIdentifier.builder().key(key).versionId(versionId).build();
    }

    private S3Error error(String key, String versionId) {
        return S3Error.builder().key(key).versionId(versionId).code("AccessDenied").message("denied").build();
    }

    private DeleteObjectsResponse success() {
        return DeleteObjectsResponse.builder().build();
    }

    private List<ObjectIdentifier> deletedIds() {
        ArgumentCaptor<DeleteObjectsRequest> request = ArgumentCaptor.forClass(DeleteObjectsRequest.class);
        verify(s3Client).deleteObjects(request.capture());
        return request.getValue().delete().objects();
    }
}
