package com.mapmory.backend.travelrecord.application;

import com.mapmory.backend.travelrecord.domain.OrphanedMediaRepository;
import com.mapmory.backend.travelrecord.domain.TravelRecordRepository;
import com.mapmory.backend.upload.service.port.ObjectKeyGenerator;
import com.mapmory.backend.upload.service.port.StoredObject;
import com.mapmory.backend.upload.service.port.StoredObjectLister;
import com.mapmory.backend.upload.service.port.StoredObjectPage;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 기록에 붙지 않은 S3 객체를 찾아 정리 큐에 넣는다. 지우는 일은 {@link OrphanedMediaCleanup}이 맡는다. (#186, ADR 0018)
 *
 * <p>서버는 업로드 URL을 발급할 때 키를 적지 않는다. 그래서 사진을 올린 뒤 저장이 실패하거나 취소되면,
 * 그리고 #162 전에 지운 기록의 사진은 키를 아는 곳이 S3뿐이다. 여행 기록 경로를 한 쪽씩 읽어 DB와 대조한다.
 *
 * <p>앱은 며칠 전에 올린 키로 저장을 다시 시도할 수 있으므로, 대조한 뒤에도 키가 기록에 붙을 수 있다.
 * 큐에 넣으면 다음 정리 배치가 지우기 직전에 record_media를 다시 보므로 그사이 붙은 키는 지워지지 않는다.
 *
 * <p>서버가 만든 형식의 키만 후보로 삼는다. 지우면 되돌릴 수 없으므로 낯선 객체는 수만 세고 건드리지 않는다.
 *
 * <p>한 쪽을 처리하지 못해도 다음 쪽으로 넘어간다. 목록 조회가 실패하면 다음 쪽을 알 수 없으므로 멈추고,
 * 그때까지의 결과를 돌려준 뒤 다음 날 처음부터 다시 읽는다.
 * 같은 키를 다시 넣어도 무시되므로 여러 번 돌아도 된다.
 */
@Component
@EnableConfigurationProperties(UnlinkedMediaProperties.class)
public class UnlinkedMediaReconciliation {

    private static final Logger log = LoggerFactory.getLogger(UnlinkedMediaReconciliation.class);
    private static final String METRIC_NAME = "mapmory.media.unlinked.reconcile";
    private static final int SAMPLE_SIZE = 5;

    private final StoredObjectLister storedObjectLister;
    private final TravelRecordRepository travelRecordRepository;
    private final OrphanedMediaRepository orphanedMediaRepository;
    private final ObjectKeyGenerator objectKeyGenerator;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;
    private final MeterRegistry meterRegistry;
    private final UnlinkedMediaProperties properties;

    public UnlinkedMediaReconciliation(StoredObjectLister storedObjectLister,
                                       TravelRecordRepository travelRecordRepository,
                                       OrphanedMediaRepository orphanedMediaRepository,
                                       ObjectKeyGenerator objectKeyGenerator,
                                       PlatformTransactionManager transactionManager,
                                       Clock clock,
                                       MeterRegistry meterRegistry,
                                       UnlinkedMediaProperties properties) {
        this.storedObjectLister = storedObjectLister;
        this.travelRecordRepository = travelRecordRepository;
        this.orphanedMediaRepository = orphanedMediaRepository;
        this.objectKeyGenerator = objectKeyGenerator;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.clock = clock;
        this.meterRegistry = meterRegistry;
        this.properties = properties;
    }

    public Result run() {
        Instant uploadedBefore = clock.instant().minus(properties.gracePeriod());
        Tally tally = new Tally();
        String token = null;
        do {
            StoredObjectPage page;
            try {
                page = storedObjectLister.listTravelRecordObjects(token);
            } catch (RuntimeException exception) {
                tally.listFailed = true;
                count("FAILED_LIST", 1);
                log.atWarn()
                        .addKeyValue("event", "UNLINKED_MEDIA_LIST_FAILED")
                        .addKeyValue("scanned", tally.scanned)
                        .setCause(exception)
                        .log("S3 객체 목록을 읽지 못해 대조를 멈췄다");
                break;
            }
            tally.scanned += page.objects().size();
            try {
                reconcile(page.objects(), uploadedBefore, tally);
            } catch (RuntimeException exception) {
                tally.failedPages++;
                count("FAILED_PAGE", 1);
                log.atWarn()
                        .addKeyValue("event", "UNLINKED_MEDIA_PAGE_FAILED")
                        .addKeyValue("count", page.objects().size())
                        .setCause(exception)
                        .log("기록에 붙지 않은 S3 객체 한 쪽을 대조하지 못했다");
            }
            token = page.nextToken();
        } while (token != null);
        return tally.toResult(properties.dryRun());
    }

    private void reconcile(List<StoredObject> objects, Instant uploadedBefore, Tally tally) {
        List<String> oldKeys = objects.stream()
                .filter(object -> object.lastModified().isBefore(uploadedBefore))
                .map(StoredObject::key)
                .toList();
        List<String> generatedKeys = oldKeys.stream().filter(objectKeyGenerator::isGenerated).toList();
        int unrecognized = oldKeys.size() - generatedKeys.size();
        tally.unrecognized += unrecognized;
        count("UNRECOGNIZED", unrecognized);

        List<String> candidates = findCandidates(generatedKeys);
        if (candidates.isEmpty()) {
            return;
        }
        if (!properties.dryRun()) {
            transactionTemplate.executeWithoutResult(status -> candidates.forEach(orphanedMediaRepository::enqueue));
            tally.queued += candidates.size();
            count("QUEUED", candidates.size());
        }
        tally.addCandidates(candidates);
        count("CANDIDATE", candidates.size());
    }

    private List<String> findCandidates(List<String> keys) {
        if (keys.isEmpty()) {
            return List.of();
        }
        Set<String> known = new HashSet<>(travelRecordRepository.findObjectKeysIn(keys));
        known.addAll(travelRecordRepository.findThumbKeysIn(keys));
        known.addAll(orphanedMediaRepository.findObjectKeysIn(keys));
        return keys.stream().filter(key -> !known.contains(key)).toList();
    }

    private void count(String outcome, int amount) {
        meterRegistry.counter(METRIC_NAME, "outcome", outcome, "dryRun", String.valueOf(properties.dryRun()))
                .increment(amount);
    }

    /**
     * @param scanned      읽은 객체 수
     * @param candidates   큐에 넣을 대상 수. dryRun이면 넣었을 수다.
     * @param queued       큐에 넣은 키 수
     * @param unrecognized 유예 기간이 지났지만 서버가 만든 형식이 아니라 건너뛴 객체 수
     * @param failedPages  대조하지 못한 쪽 수
     * @param listFailed   목록 조회가 실패해 끝까지 읽지 못했는가
     * @param sample       후보에서 무작위로 고른 키. dryRun에서 후보가 정말 고아인지 볼 때 쓴다.
     */
    public record Result(
            int scanned,
            int candidates,
            int queued,
            int unrecognized,
            int failedPages,
            boolean listFailed,
            boolean dryRun,
            List<String> sample
    ) {
    }

    private static final class Tally {

        private int scanned;
        private int candidates;
        private int queued;
        private int unrecognized;
        private int failedPages;
        private boolean listFailed;
        private final List<String> sample = new ArrayList<>();

        private void addCandidates(List<String> keys) {
            keys.forEach(this::addToSample);
        }

        // S3 목록은 사전순이라 앞의 키만 남기면 날마다 같은 키만 보게 된다. 후보 전체에서 고르게 뽑는다(reservoir sampling).
        private void addToSample(String key) {
            candidates++;
            if (sample.size() < SAMPLE_SIZE) {
                sample.add(key);
                return;
            }
            int slot = ThreadLocalRandom.current().nextInt(candidates);
            if (slot < SAMPLE_SIZE) {
                sample.set(slot, key);
            }
        }

        private Result toResult(boolean dryRun) {
            return new Result(scanned, candidates, queued, unrecognized, failedPages, listFailed, dryRun,
                    List.copyOf(sample));
        }
    }
}
