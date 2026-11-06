package com.mapmory.backend.travelrecord.application;

import com.mapmory.backend.member.api.MemberDataPurger;
import com.mapmory.backend.travelrecord.domain.TravelRecordRepository;
import com.mapmory.backend.upload.service.port.ObjectDeleter;
import java.util.ArrayList;
import java.util.List;
import org.springframework.core.annotation.Order;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

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

    TravelRecordPurger(TravelRecordRepository travelRecordRepository, ObjectDeleter objectDeleter) {
        this.travelRecordRepository = travelRecordRepository;
        this.objectDeleter = objectDeleter;
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
        List<String> keys = new ArrayList<>(travelRecordRepository.findObjectKeysByTravelRecordIdIn(recordIds));
        keys.addAll(travelRecordRepository.findThumbKeysByTravelRecordIdIn(recordIds));
        List<String> failed = objectDeleter.delete(keys);
        if (!failed.isEmpty()) {
            throw new IllegalStateException("사진 객체를 지우지 못했다. memberId=" + memberId + ", keys=" + failed.size());
        }
        travelRecordRepository.deleteAllByIdIn(recordIds);
    }
}
