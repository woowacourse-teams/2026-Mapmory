package com.mapmory.backend.travelrecord.application;

import com.mapmory.backend.travelrecord.domain.OrphanedMedia;
import com.mapmory.backend.travelrecord.domain.OrphanedMediaRepository;
import com.mapmory.backend.travelrecord.domain.TravelRecordRepository;
import com.mapmory.backend.upload.service.port.ObjectDeleter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

/**
 * 기록에서 떨어져 나간 S3 객체를 지운다. 지운 객체는 되돌릴 수 없다. (#162, ADR 0018)
 *
 * <p>기록 삭제·수정과 같은 트랜잭션에서 키를 적고 여기서 지우므로, 삭제가 롤백되면 객체도 지워지지 않고 S3가 실패해도 키를 잃지 않는다.
 * 사진을 뺐다가 다음 수정에서 다시 붙일 수 있으므로 지우기 직전에 record_media를 다시 본다. 다시 쓰이는 키는 지우지 않고 행만 없앤다.
 * 실패한 키는 행이 남아 다음 날 다시 지운다.
 */
@Component
public class OrphanedMediaCleanup {

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

        Set<String> failed = new HashSet<>(objectDeleter.delete(orphaned));

        List<String> finished = new ArrayList<>(keys);
        finished.removeAll(failed);
        if (!finished.isEmpty()) {
            orphanedMediaRepository.deleteAllByObjectKeyIn(finished);
        }
        return orphaned.size() - failed.size();
    }
}
