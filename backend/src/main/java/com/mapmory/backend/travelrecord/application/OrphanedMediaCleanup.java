package com.mapmory.backend.travelrecord.application;

import com.mapmory.backend.travelrecord.domain.OrphanedMedia;
import com.mapmory.backend.travelrecord.domain.OrphanedMediaRepository;
import com.mapmory.backend.travelrecord.domain.TravelRecordRepository;
import com.mapmory.backend.upload.service.port.ObjectDeleter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

/**
 * 기록에서 떨어져 나간 S3 객체를 지운다. 지운 객체는 되돌릴 수 없다. (#162, ADR 0018)
 *
 * <p>기록 삭제·수정과 같은 트랜잭션에서 키를 적고 여기서 지우므로, 삭제가 롤백되면 객체도 지워지지 않고 S3가 실패해도 키를 잃지 않는다.
 * 사진을 뺐다가 다음 수정에서 다시 붙일 수 있으므로 지우기 직전에 record_media를 다시 본다. 다시 쓰이는 키는 지우지 않고 행만 없앤다.
 * 실패한 키는 행이 남아 다음 날 다시 지운다.
 *
 * <p>우리 경로 밖의 키는 {@link ObjectDeleter}가 묶음째 거절하므로 미리 빼서 뒤의 키가 막히지 않게 한다.
 * 설정이 바뀌어 생긴 키일 수 있어 지우거나 큐에서 없애지 않고 행을 남긴 채 경고한다.
 */
@Component
public class OrphanedMediaCleanup {

    private static final Logger log = LoggerFactory.getLogger(OrphanedMediaCleanup.class);
    private static final int BATCH_SIZE = 500;

    private final OrphanedMediaRepository orphanedMediaRepository;
    private final TravelRecordRepository travelRecordRepository;
    private final ObjectDeleter objectDeleter;

    public OrphanedMediaCleanup(OrphanedMediaRepository orphanedMediaRepository,
                                TravelRecordRepository travelRecordRepository,
                                ObjectDeleter objectDeleter) {
        this.orphanedMediaRepository = orphanedMediaRepository;
        this.travelRecordRepository = travelRecordRepository;
        this.objectDeleter = objectDeleter;
    }

    /**
     * @return S3에서 지운 객체 수
     */
    public int run() {
        int deleted = 0;
        long after = 0;
        List<OrphanedMedia> batch;
        do {
            batch = orphanedMediaRepository.findByIdGreaterThanOrderById(after, PageRequest.of(0, BATCH_SIZE));
            if (!batch.isEmpty()) {
                deleted += cleanUp(batch.stream().map(OrphanedMedia::getObjectKey).toList());
                after = batch.getLast().getId();
            }
        } while (batch.size() == BATCH_SIZE);
        return deleted;
    }

    private int cleanUp(List<String> keys) {
        Set<String> inUse = new HashSet<>(travelRecordRepository.findObjectKeysIn(keys));
        inUse.addAll(travelRecordRepository.findThumbKeysIn(keys));
        List<String> orphaned = keys.stream().filter(key -> !inUse.contains(key)).toList();
        List<String> deletable = orphaned.stream().filter(objectDeleter::owns).toList();
        List<String> outside = orphaned.stream().filter(key -> !objectDeleter.owns(key)).toList();
        warnIfOutside(outside.size());

        Set<String> kept = new HashSet<>(objectDeleter.delete(deletable));
        int deleted = deletable.size() - kept.size();
        kept.addAll(outside);

        List<String> finished = new ArrayList<>(keys);
        finished.removeAll(kept);
        if (!finished.isEmpty()) {
            orphanedMediaRepository.deleteAllByObjectKeyIn(finished);
        }
        return deleted;
    }

    private void warnIfOutside(int count) {
        if (count > 0) {
            log.atWarn()
                    .addKeyValue("event", "ORPHANED_MEDIA_KEY_OUTSIDE_PREFIX")
                    .addKeyValue("count", count)
                    .log("우리 경로 밖의 키라 지우지 않고 큐에 남겼다");
        }
    }
}
