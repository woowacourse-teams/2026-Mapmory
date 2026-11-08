package com.mapmory.backend.travelrecord.thumbnail;

import static java.util.Comparator.reverseOrder;

import com.mapmory.backend.sharevideo.worker.RenderActivity;
import com.mapmory.backend.travelrecord.domain.MediaWithoutThumbnail;
import com.mapmory.backend.travelrecord.domain.TravelRecordRepository;
import com.mapmory.backend.travelrecord.thumbnail.ThumbnailMaker.Outcome;
import com.mapmory.backend.upload.storage.DerivedObjectKeys;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * 썸네일이 없는 사진을 차례로 찾아 썸네일을 만들고 thumb_key를 채운다. 렌더러 서버에서만 켠다(ADR 0029).
 *
 * <p>할 일 목록은 thumb_key가 비어 있다는 상태 자체이고, 썸네일 키는 원본 키에서 정해진다. 그래서 작업 테이블이나 리스 없이
 * 몇 번을 다시 해도 같은 결과가 된다. 배포하면 기존 사진도 같은 방식으로 채워진다(백필).
 *
 * <ul>
 *   <li>thumb_key에는 S3에 올라간 것을 확인한 키만 넣는다. 그 전에는 비어 있어 앱은 원본을 본다.</li>
 *   <li>올린 뒤 채우기 전에 사진 행이 사라졌으면(사진 빼기, 기록 삭제, 회원 파기) 만든 썸네일을 지운다.</li>
 *   <li>같은 사진이 maxFailures번 실패하면 프로세스가 다시 뜰 때까지 건너뛴다. 원본 내용 때문에 못 만들면 바로 건너뛴다.</li>
 *   <li>같은 서버에서 공유 영상을 만드는 동안에는 쉰다. 메모리를 나눠 쓰고, 썸네일은 늦어져도 원본이 보인다.</li>
 * </ul>
 *
 * <p>상태는 스케줄러 스레드 하나만 만진다(전용 스케줄러, 스레드 1개).
 */
public class ThumbnailWorker {

    private static final Logger log = LoggerFactory.getLogger(ThumbnailWorker.class);
    private static final String METRIC = "mapmory.thumbnail.worker";
    private static final Duration BACKLOG_AGE = Duration.ofHours(1);

    private final TravelRecordRepository travelRecordRepository;
    private final DerivedObjectKeys derivedObjectKeys;
    private final ThumbnailStorage storage;
    private final ThumbnailMaker maker;
    private final RenderActivity renderActivity;
    private final ThumbnailWorkerProperties properties;
    private final MeterRegistry meterRegistry;
    private final Clock clock;

    private final Map<Long, Integer> failures = new HashMap<>();
    private final Set<Long> unsupported = new HashSet<>();
    private final AtomicLong backlog = new AtomicLong();
    private long cursor;

    public ThumbnailWorker(
            TravelRecordRepository travelRecordRepository,
            DerivedObjectKeys derivedObjectKeys,
            ThumbnailStorage storage,
            ThumbnailMaker maker,
            RenderActivity renderActivity,
            ThumbnailWorkerProperties properties,
            MeterRegistry meterRegistry,
            Clock clock
    ) {
        this.travelRecordRepository = travelRecordRepository;
        this.derivedObjectKeys = derivedObjectKeys;
        this.storage = storage;
        this.maker = maker;
        this.renderActivity = renderActivity;
        this.properties = properties;
        this.meterRegistry = meterRegistry;
        this.clock = clock;
        Gauge.builder("mapmory.thumbnail.backlog", backlog, AtomicLong::get)
                .description("한 시간이 넘었는데 썸네일이 없는 사진 수. 한 바퀴를 돌 때마다 갱신한다")
                .register(meterRegistry);
    }

    @Scheduled(fixedDelayString = "${media.thumbnail.worker.poll-delay}", scheduler = "thumbnailTaskScheduler")
    public void processNext() {
        try {
            processBatch();
        } catch (Exception e) {
            log.error("썸네일 작업 차례를 마치지 못했습니다. cursor={}", cursor, e);
        }
    }

    private void processBatch() {
        if (renderActivity.isRendering()) {
            return;
        }
        List<MediaWithoutThumbnail> batch = travelRecordRepository.findMediaWithoutThumbnail(
                cursor, PageRequest.of(0, properties.batchSize()));
        for (MediaWithoutThumbnail media : batch) {
            if (renderActivity.isRendering()) {
                return;
            }
            cursor = media.id();
            process(media);
        }
        if (batch.size() < properties.batchSize()) {
            cursor = 0;
            backlog.set(travelRecordRepository.countMediaWithoutThumbnailCreatedBefore(
                    LocalDateTime.now(clock).minus(BACKLOG_AGE)));
        }
    }

    private void process(MediaWithoutThumbnail media) {
        if (failures.getOrDefault(media.id(), 0) >= properties.maxFailures()) {
            return;
        }
        Optional<String> thumbnailKey = derivedObjectKeys.thumbnailOf(media.objectKey());
        if (thumbnailKey.isEmpty()) {
            if (unsupported.add(media.id())) {
                count("unsupported_key");
                log.info("우리 원본 형식이 아니라 썸네일을 만들지 않습니다. mediaId={}, objectKey={}",
                        media.id(), media.objectKey());
            }
            return;
        }
        Path workDirectory = null;
        try {
            if (storage.exists(thumbnailKey.get())) {
                count("attached_existing");
            } else {
                workDirectory = createWorkDirectory(media.id());
                if (!create(media, thumbnailKey.get(), workDirectory)) {
                    return;
                }
            }
            attach(media.id(), thumbnailKey.get());
            failures.remove(media.id());
        } catch (Exception e) {
            int failed = failures.merge(media.id(), 1, Integer::sum);
            count("failed");
            log.error("썸네일을 만들지 못했습니다. mediaId={}, objectKey={}, failures={}/{}",
                    media.id(), media.objectKey(), failed, properties.maxFailures(), e);
            if (failed >= properties.maxFailures()) {
                count("given_up");
            }
        } finally {
            if (workDirectory != null) {
                deleteWorkDirectory(workDirectory);
            }
        }
    }

    private boolean create(MediaWithoutThumbnail media, String thumbnailKey, Path workDirectory) {
        Path original = workDirectory.resolve("original");
        Path thumbnail = workDirectory.resolve("thumbnail.webp");
        storage.download(media.objectKey(), original);
        if (maker.make(original, thumbnail) == Outcome.UNDECODABLE) {
            failures.put(media.id(), properties.maxFailures());
            count("undecodable");
            log.warn("원본 내용 때문에 썸네일을 만들 수 없어 원본으로 둡니다. mediaId={}, objectKey={}",
                    media.id(), media.objectKey());
            return false;
        }
        storage.upload(thumbnail, thumbnailKey);
        count("created");
        return true;
    }

    private void attach(Long mediaId, String thumbnailKey) {
        if (travelRecordRepository.attachThumbnail(mediaId, thumbnailKey) > 0) {
            return;
        }
        if (!travelRecordRepository.existsMediaById(mediaId)) {
            storage.delete(thumbnailKey);
            count("orphan_deleted");
        }
    }

    private Path createWorkDirectory(Long mediaId) throws IOException {
        Path root = properties.workDirectory().toAbsolutePath().normalize();
        Files.createDirectories(root);
        return Files.createTempDirectory(root, "media-" + mediaId + "-");
    }

    private void deleteWorkDirectory(Path workDirectory) {
        try (Stream<Path> paths = Files.walk(workDirectory)) {
            paths.sorted(reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException e) {
                    log.warn("썸네일 임시 파일을 지우지 못했습니다. path={}", path, e);
                }
            });
        } catch (IOException e) {
            log.warn("썸네일 임시 디렉터리를 정리하지 못했습니다. path={}", workDirectory, e);
        }
    }

    private void count(String result) {
        Counter.builder(METRIC).tag("result", result).register(meterRegistry).increment();
    }
}
