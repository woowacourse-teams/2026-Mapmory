package com.mapmory.backend.travelrecord.application;

import com.mapmory.backend.member.api.MemberDataPurger;
import com.mapmory.backend.travelrecord.domain.OrphanedMediaRepository;
import com.mapmory.backend.travelrecord.domain.TravelRecordRepository;
import com.mapmory.backend.upload.service.port.ObjectDeleter;
import com.mapmory.backend.upload.storage.DerivedObjectKeys;
import java.util.ArrayList;
import java.util.List;
import org.springframework.core.annotation.Order;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 회원의 여행 기록을 사진 원본부터 지운다. 사진·태그 연결 행은 기록을 지울 때 DB가 함께 지운다(ON DELETE CASCADE).
 *
 * <p>나눠서 지운다. 끝난 묶음은 그대로 두고 실패한 묶음부터 다음 날 다시 지운다.
 */
@Component
@Order(2)
class TravelRecordPurger implements MemberDataPurger {

    private static final int BATCH_SIZE = 100;

    private final TravelRecordRepository travelRecordRepository;
    private final ObjectDeleter objectDeleter;
    private final OrphanedMediaRepository orphanedMediaRepository;
    private final DerivedObjectKeys derivedObjectKeys;
    private final TransactionTemplate transactionTemplate;

    TravelRecordPurger(
            TravelRecordRepository travelRecordRepository,
            ObjectDeleter objectDeleter,
            OrphanedMediaRepository orphanedMediaRepository,
            DerivedObjectKeys derivedObjectKeys,
            PlatformTransactionManager transactionManager
    ) {
        this.travelRecordRepository = travelRecordRepository;
        this.objectDeleter = objectDeleter;
        this.orphanedMediaRepository = orphanedMediaRepository;
        this.derivedObjectKeys = derivedObjectKeys;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Override
    public void purge(Long memberId) {
        List<Long> recordIds;
        do {
            recordIds = travelRecordRepository.findIdsByMemberId(memberId, PageRequest.of(0, BATCH_SIZE));
            if (!recordIds.isEmpty()) {
                purgeBatch(memberId, recordIds);
            }
        } while (recordIds.size() == BATCH_SIZE);
    }

    private void purgeBatch(Long memberId, List<Long> recordIds) {
        List<String> objectKeys = travelRecordRepository.findObjectKeysByTravelRecordIdIn(recordIds);
        List<String> keys = new ArrayList<>(objectKeys);
        keys.addAll(travelRecordRepository.findThumbKeysByTravelRecordIdIn(recordIds));
        List<String> failed = objectDeleter.delete(keys);
        if (!failed.isEmpty()) {
            throw new IllegalStateException("사진 객체를 지우지 못했다. memberId=" + memberId + ", keys=" + failed.size());
        }
        // 위에서 S3를 지우는 동안 썸네일 워커(렌더러)가 썸네일을 올리고 thumb_key를 채웠을 수 있다. 행을 지운 뒤
        // 원본에서 만들어질 썸네일 키를 정리 큐에 넣어, 그 썸네일도 정리 배치가 지우게 한다(ADR 0029).
        transactionTemplate.executeWithoutResult(status -> {
            travelRecordRepository.deleteAllByIdIn(recordIds);
            objectKeys.stream()
                    .flatMap(key -> derivedObjectKeys.thumbnailOf(key).stream())
                    .distinct()
                    .forEach(orphanedMediaRepository::enqueue);
        });
    }
}
