package com.mapmory.backend.group.application;

import com.mapmory.backend.group.domain.Album;
import com.mapmory.backend.group.domain.AlbumRepository;
import com.mapmory.backend.group.domain.TravelGroupRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 지워진 기록의 항목을 모든 앨범에서 뗀다(T7). 항목이 없어진 앨범은 지운다(ADR 0022 2026-10-29 보완).
 *
 * <p>기록 삭제가 커밋된 뒤 불린다. 그 트랜잭션 자원이 아직 묶여 있으므로 새 트랜잭션(REQUIRES_NEW)에서 한다.
 * 그룹마다 따로 하고, 그룹 행 잠금을 첫 문장으로 둔다. 잠금보다 먼저 일반 SELECT를 하면 스냅샷이 잠금 전에 정해져
 * 그사이 다른 요청이 담은 항목을 못 본다.
 */
@Service
public class AlbumEntryCleaner {

    private final AlbumRepository albumRepository;
    private final TravelGroupRepository travelGroupRepository;
    private final TransactionTemplate readTransaction;
    private final TransactionTemplate writeTransaction;

    public AlbumEntryCleaner(AlbumRepository albumRepository,
                             TravelGroupRepository travelGroupRepository,
                             PlatformTransactionManager transactionManager) {
        this.albumRepository = albumRepository;
        this.travelGroupRepository = travelGroupRepository;
        this.readTransaction = new TransactionTemplate(transactionManager);
        this.readTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.readTransaction.setReadOnly(true);
        this.writeTransaction = new TransactionTemplate(transactionManager);
        this.writeTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public void removeRecord(Long travelRecordId) {
        List<Long> groupIds = readTransaction.execute(
                status -> albumRepository.findGroupIdsContainingRecord(travelRecordId));
        groupIds.forEach(groupId -> writeTransaction.executeWithoutResult(
                status -> removeRecordInGroup(groupId, travelRecordId)));
    }

    private void removeRecordInGroup(Long groupId, Long travelRecordId) {
        // 그사이 그룹이 지워졌으면(마지막 멤버가 나감) 앨범도 함께 지워졌다.
        if (travelGroupRepository.findByIdForUpdate(groupId).isEmpty()) {
            return;
        }
        for (Album album : albumRepository.findAllByGroupIdContainingRecord(groupId, travelRecordId)) {
            album.removeEntriesOf(travelRecordId);
            if (album.isEmpty()) {
                albumRepository.delete(album);
            }
        }
    }
}
