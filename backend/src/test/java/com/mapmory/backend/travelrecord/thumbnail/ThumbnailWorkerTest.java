package com.mapmory.backend.travelrecord.thumbnail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mapmory.backend.travelrecord.domain.MediaWithoutThumbnail;
import com.mapmory.backend.travelrecord.domain.TravelRecordRepository;
import com.mapmory.backend.travelrecord.thumbnail.ThumbnailMaker.Outcome;
import com.mapmory.backend.upload.storage.DerivedObjectKeys;
import com.mapmory.backend.upload.storage.S3StorageProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.domain.Pageable;

class ThumbnailWorkerTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-08T12:00:00Z"), ZoneOffset.UTC);
    private static final MediaWithoutThumbnail PHOTO = new MediaWithoutThumbnail(10L, "mapmory/travel-records/7/abc.heic");
    private static final String THUMBNAIL = "mapmory/derived/thumb/7/abc.webp";

    @TempDir
    Path workRoot;

    private final TravelRecordRepository repository = mock(TravelRecordRepository.class);
    private final FakeStorage storage = new FakeStorage();
    private final FakeMaker maker = new FakeMaker();
    private final AtomicBoolean rendering = new AtomicBoolean();
    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private ThumbnailWorker worker;

    @BeforeEach
    void setUp() {
        S3StorageProperties s3 = new S3StorageProperties("bucket", "ap-northeast-2", "mapmory", Duration.ofMinutes(5),
                new S3StorageProperties.Client(Duration.ofSeconds(1), Duration.ofSeconds(3), 3,
                        Duration.ofSeconds(10), Duration.ofSeconds(30), Duration.ofSeconds(10), Duration.ofSeconds(30)));
        ThumbnailWorkerProperties properties = new ThumbnailWorkerProperties(true, Duration.ofSeconds(10), 2, 1080,
                Duration.ofSeconds(30), 3, Path.of("."), "python3", workRoot, Duration.ofSeconds(30));
        worker = new ThumbnailWorker(repository, new DerivedObjectKeys(s3), storage, maker, rendering::get,
                properties, meterRegistry, CLOCK);
        when(repository.attachThumbnail(anyLong(), any())).thenReturn(1);
    }

    @Test
    void 썸네일을_만들어_올리고_thumb_key를_채운다() {
        pending(PHOTO);

        worker.processNext();

        assertThat(storage.downloaded).containsExactly(PHOTO.objectKey());
        assertThat(storage.uploaded).containsExactly(THUMBNAIL);
        verify(repository).attachThumbnail(10L, THUMBNAIL);
        assertThat(count("created")).isEqualTo(1);
    }

    // 이전 실행이 올린 직후 죽었으면 썸네일은 S3에 있고 thumb_key만 비어 있다.
    @Test
    void 썸네일이_이미_S3에_있으면_만들지_않고_채우기만_한다() {
        pending(PHOTO);
        storage.keys.add(THUMBNAIL);

        worker.processNext();

        assertThat(maker.calls).isZero();
        assertThat(storage.uploaded).isEmpty();
        verify(repository).attachThumbnail(10L, THUMBNAIL);
    }

    @Test
    void 원본_내용_때문에_만들_수_없으면_다시_하지_않는다() {
        pending(PHOTO);
        maker.outcome = Outcome.UNDECODABLE;

        worker.processNext();
        worker.processNext();

        assertThat(maker.calls).isEqualTo(1);
        assertThat(storage.uploaded).isEmpty();
        verify(repository, never()).attachThumbnail(anyLong(), any());
        assertThat(count("undecodable")).isEqualTo(1);
    }

    @Test
    void 실패하면_상한까지만_다시_한다() {
        pending(PHOTO);
        maker.failure = new ThumbnailMakeException("exitCode=137");

        for (int i = 0; i < 5; i++) {
            worker.processNext();
        }

        assertThat(maker.calls).isEqualTo(3);
        assertThat(count("failed")).isEqualTo(3);
        assertThat(count("given_up")).isEqualTo(1);
    }

    @Test
    void 다시_해서_성공하면_실패_횟수를_잊는다() {
        pending(PHOTO);
        maker.failure = new ThumbnailMakeException("exitCode=1");
        worker.processNext();
        worker.processNext();
        maker.failure = null;

        worker.processNext();

        verify(repository).attachThumbnail(10L, THUMBNAIL);
    }

    // 올린 뒤 채우기 전에 사진이 빠지거나 기록이 지워지면 행이 없다. 썸네일만 남지 않게 지운다.
    @Test
    void 채우는_사이_사진_행이_사라졌으면_만든_썸네일을_지운다() {
        pending(PHOTO);
        when(repository.attachThumbnail(10L, THUMBNAIL)).thenReturn(0);
        when(repository.existsMediaById(10L)).thenReturn(false);

        worker.processNext();

        assertThat(storage.deleted).containsExactly(THUMBNAIL);
        assertThat(count("orphan_deleted")).isEqualTo(1);
    }

    @Test
    void 이미_다른_값으로_채워졌으면_썸네일을_지우지_않는다() {
        pending(PHOTO);
        when(repository.attachThumbnail(10L, THUMBNAIL)).thenReturn(0);
        when(repository.existsMediaById(10L)).thenReturn(true);

        worker.processNext();

        assertThat(storage.deleted).isEmpty();
    }

    @Test
    void 영상을_렌더링하는_중이면_아무것도_하지_않는다() {
        rendering.set(true);

        worker.processNext();

        verify(repository, never()).findMediaWithoutThumbnail(anyLong(), any());
    }

    @Test
    void 처리_중에_영상_렌더링이_시작되면_멈추고_다음_차례에_이어간다() {
        MediaWithoutThumbnail second = new MediaWithoutThumbnail(11L, "mapmory/travel-records/7/def.jpg");
        when(repository.findMediaWithoutThumbnail(eq(0L), any(Pageable.class))).thenReturn(List.of(PHOTO, second));
        when(repository.findMediaWithoutThumbnail(eq(10L), any(Pageable.class))).thenReturn(List.of(second));
        maker.onMake = () -> rendering.set(true);

        worker.processNext();

        assertThat(maker.calls).isEqualTo(1);
        rendering.set(false);
        maker.onMake = null;
        worker.processNext();
        verify(repository).attachThumbnail(11L, "mapmory/derived/thumb/7/def.webp");
    }

    @Test
    void 한_쪽을_다_채우면_커서를_옮기고_덜_차면_처음으로_돌아간다() {
        MediaWithoutThumbnail second = new MediaWithoutThumbnail(11L, "mapmory/travel-records/7/def.jpg");
        when(repository.findMediaWithoutThumbnail(eq(0L), any(Pageable.class))).thenReturn(List.of(PHOTO, second));
        when(repository.findMediaWithoutThumbnail(eq(11L), any(Pageable.class))).thenReturn(List.of());

        worker.processNext();
        worker.processNext();
        worker.processNext();

        verify(repository).findMediaWithoutThumbnail(eq(11L), any(Pageable.class));
        assertThat(maker.calls).isEqualTo(2);
    }

    // 접두사를 붙이기 전에 저장된 옛 키는 우리 원본 형식이 아니라 썸네일을 만들지 않는다.
    @Test
    void 우리_원본_형식이_아닌_키는_건너뛰고_한_번만_센다() {
        pending(new MediaWithoutThumbnail(12L, "travel-records/7/old.jpg"));

        worker.processNext();
        worker.processNext();

        assertThat(storage.calls).isZero();
        assertThat(count("unsupported_key")).isEqualTo(1);
    }

    @Test
    void 저장소나_DB가_실패해도_예외를_던지지_않고_다음_차례에_이어간다() {
        when(repository.findMediaWithoutThumbnail(anyLong(), any())).thenThrow(new QueryTimeoutException("db"));
        assertThatCode(worker::processNext).doesNotThrowAnyException();

        org.mockito.Mockito.doReturn(List.of(PHOTO)).when(repository).findMediaWithoutThumbnail(anyLong(), any());
        storage.failure = new RuntimeException("s3 down");
        assertThatCode(worker::processNext).doesNotThrowAnyException();
    }

    @Test
    void 작업_디렉터리를_남기지_않는다() throws IOException {
        pending(PHOTO);
        worker.processNext();
        maker.failure = new ThumbnailMakeException("exitCode=1");
        pending(new MediaWithoutThumbnail(11L, "mapmory/travel-records/7/def.jpg"));
        worker.processNext();

        try (Stream<Path> files = Files.list(workRoot)) {
            assertThat(files).isEmpty();
        }
    }

    @Test
    void 한_바퀴를_돌면_한_시간_넘게_썸네일이_없는_사진_수를_낸다() {
        pending();
        when(repository.countMediaWithoutThumbnailCreatedBefore(LocalDateTime.of(2026, 11, 8, 11, 0))).thenReturn(4L);

        worker.processNext();

        assertThat(meterRegistry.get("mapmory.thumbnail.backlog").gauge().value()).isEqualTo(4.0);
    }

    private void pending(MediaWithoutThumbnail... photos) {
        when(repository.findMediaWithoutThumbnail(anyLong(), any(Pageable.class))).thenReturn(List.of(photos));
    }

    private double count(String result) {
        var counter = meterRegistry.find("mapmory.thumbnail.worker").tag("result", result).counter();
        return counter == null ? 0 : counter.count();
    }

    private static class FakeStorage implements ThumbnailStorage {
        final Set<String> keys = new HashSet<>();
        final List<String> downloaded = new ArrayList<>();
        final List<String> uploaded = new ArrayList<>();
        final List<String> deleted = new ArrayList<>();
        RuntimeException failure;
        int calls;

        @Override
        public boolean exists(String thumbnailKey) {
            touch();
            return keys.contains(thumbnailKey);
        }

        @Override
        public void download(String originalKey, Path destination) {
            touch();
            try {
                Files.writeString(destination, "original");
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
            downloaded.add(originalKey);
        }

        @Override
        public void upload(Path source, String thumbnailKey) {
            touch();
            assertThat(source).exists();
            uploaded.add(thumbnailKey);
            keys.add(thumbnailKey);
        }

        @Override
        public void delete(String thumbnailKey) {
            touch();
            deleted.add(thumbnailKey);
        }

        private void touch() {
            calls++;
            if (failure != null) {
                throw failure;
            }
        }
    }

    private static class FakeMaker implements ThumbnailMaker {
        Outcome outcome = Outcome.CREATED;
        RuntimeException failure;
        Runnable onMake;
        int calls;

        @Override
        public Outcome make(Path original, Path thumbnail) {
            calls++;
            if (onMake != null) {
                onMake.run();
            }
            if (failure != null) {
                throw failure;
            }
            try {
                Files.writeString(thumbnail, "webp");
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
            return outcome;
        }
    }
}
