package com.mapmory.backend.travelrecord.application;

import com.mapmory.backend.travelrecord.domain.OrphanedMediaRepository;
import com.mapmory.backend.travelrecord.domain.TravelRecordRepository;
import com.mapmory.backend.upload.service.port.ObjectDeleter;
import com.mapmory.backend.upload.service.port.StoredObject;
import com.mapmory.backend.upload.service.port.StoredObjectLister;
import com.mapmory.backend.upload.service.port.StoredObjectPage;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 기록에 붙지 않은 S3 객체를 지운다. 지운 객체는 되돌릴 수 없다. (#186, ADR 0018)
 *
 * <p>서버는 업로드 URL을 발급할 때 키를 적지 않는다. 그래서 사진을 올린 뒤 저장이 실패하거나 취소되면,
 * 그리고 #615 전에 지운 기록의 사진은 키를 아는 곳이 S3뿐이다. 여행 기록 경로를 한 쪽씩 읽어 DB와 대조한다.
 *
 * <p>지우는 것은 다음을 모두 만족하는 객체다.
 * <ul>
 *   <li>유예 기간보다 먼저 올라갔다. 앱은 저장할 때 사진을 올리고 바로 저장을 부르므로 그 사이의 객체를 건드리지 않는다.</li>
 *   <li>어느 기록의 원본·썸네일 키도 아니다.</li>
 *   <li>정리 큐(orphaned_media)에 없다. 큐에 있는 키는 {@link OrphanedMediaCleanup}이 지운다.</li>
 * </ul>
 *
 * <p>DB 확인 직후 지운다. 그 사이에 유예 기간보다 오래된 객체가 기록에 붙으면 지워질 수 있다.
 * 앱은 저장에 실패하면 새 키로 다시 올리므로(MEDIA_NOT_UPLOADED) 사흘 전 키로 저장하는 경우는 드물어 잠그지 않는다.
 *
 * <p>한 쪽이 실패해도 다음 쪽으로 넘어간다. 목록 조회가 실패하면 다음 쪽을 알 수 없으므로 거기서 멈추고 다음 날 처음부터 다시 읽는다.
 */
@Component
public class UnlinkedMediaCleanup {

    private static final Logger log = LoggerFactory.getLogger(UnlinkedMediaCleanup.class);
    private static final int SAMPLE_SIZE = 5;

    private final StoredObjectLister storedObjectLister;
    private final TravelRecordRepository travelRecordRepository;
    private final OrphanedMediaRepository orphanedMediaRepository;
    private final ObjectDeleter objectDeleter;
    private final Clock clock;
    private final UnlinkedMediaCleanupProperties properties;

    public UnlinkedMediaCleanup(StoredObjectLister storedObjectLister,
                                TravelRecordRepository travelRecordRepository,
                                OrphanedMediaRepository orphanedMediaRepository,
                                ObjectDeleter objectDeleter,
                                Clock clock,
                                UnlinkedMediaCleanupProperties properties) {
        this.storedObjectLister = storedObjectLister;
        this.travelRecordRepository = travelRecordRepository;
        this.orphanedMediaRepository = orphanedMediaRepository;
        this.objectDeleter = objectDeleter;
        this.clock = clock;
        this.properties = properties;
    }

    public Result run() {
        Instant uploadedBefore = clock.instant().minus(properties.gracePeriod());
        Tally tally = new Tally();
        String token = null;
        do {
            StoredObjectPage page = storedObjectLister.listTravelRecordObjects(token);
            tally.scanned += page.objects().size();
            try {
                cleanUp(page.objects(), uploadedBefore, tally);
            } catch (RuntimeException exception) {
                tally.failedPages++;
                log.atWarn()
                        .addKeyValue("event", "UNLINKED_MEDIA_PAGE_FAILED")
                        .addKeyValue("count", page.objects().size())
                        .setCause(exception)
                        .log("기록에 붙지 않은 S3 객체 한 쪽을 정리하지 못했다");
            }
            token = page.nextToken();
        } while (token != null);
        return tally.toResult(properties.dryRun());
    }

    private void cleanUp(List<StoredObject> objects, Instant uploadedBefore, Tally tally) {
        List<String> oldKeys = objects.stream()
                .filter(object -> object.lastModified().isBefore(uploadedBefore))
                .map(StoredObject::key)
                .filter(key -> !key.endsWith("/"))
                .filter(objectDeleter::owns)
                .toList();
        if (oldKeys.isEmpty()) {
            return;
        }

        Set<String> known = new HashSet<>(travelRecordRepository.findObjectKeysIn(oldKeys));
        known.addAll(travelRecordRepository.findThumbKeysIn(oldKeys));
        known.addAll(orphanedMediaRepository.findObjectKeysIn(oldKeys));
        List<String> unlinked = oldKeys.stream().filter(key -> !known.contains(key)).toList();
        tally.addCandidates(unlinked);
        if (properties.dryRun() || unlinked.isEmpty()) {
            return;
        }

        List<String> failed = objectDeleter.delete(unlinked);
        tally.deleted += unlinked.size() - failed.size();
        tally.failed += failed.size();
    }

    /**
     * @param scanned     읽은 객체 수
     * @param candidates  지울 대상(dryRun이면 지웠을 객체) 수
     * @param deleted     지운 객체 수
     * @param failed      S3에서 지우지 못한 객체 수. 다음 날 다시 후보가 된다.
     * @param failedPages 정리하지 못한 쪽 수
     * @param sample      후보 키 몇 개. dryRun에서 후보가 정말 고아인지 볼 때 쓴다.
     */
    public record Result(
            int scanned,
            int candidates,
            int deleted,
            int failed,
            int failedPages,
            boolean dryRun,
            List<String> sample
    ) {
    }

    private static final class Tally {

        private int scanned;
        private int candidates;
        private int deleted;
        private int failed;
        private int failedPages;
        private final List<String> sample = new ArrayList<>();

        private void addCandidates(List<String> keys) {
            candidates += keys.size();
            keys.stream().limit(SAMPLE_SIZE - sample.size()).forEach(sample::add);
        }

        private Result toResult(boolean dryRun) {
            return new Result(scanned, candidates, deleted, failed, failedPages, dryRun, List.copyOf(sample));
        }
    }
}
