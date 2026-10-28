package com.mapmory.backend.group.application;

import com.mapmory.backend.common.exception.BusinessException;
import com.mapmory.backend.group.api.AlbumUpdated;
import com.mapmory.backend.group.domain.Album;
import com.mapmory.backend.group.domain.AlbumRepository;
import com.mapmory.backend.group.domain.GroupMember;
import com.mapmory.backend.group.domain.TravelGroup;
import com.mapmory.backend.group.domain.TravelGroupRepository;
import com.mapmory.backend.group.exception.GroupErrorCode;
import com.mapmory.backend.region.domain.Region;
import com.mapmory.backend.region.domain.RegionRepository;
import com.mapmory.backend.region.exception.RegionErrorCode;
import com.mapmory.backend.travelrecord.api.RecordBrief;
import com.mapmory.backend.travelrecord.api.TravelRecordQuery;
import com.mapmory.backend.travelrecord.exception.TravelRecordErrorCode;
import java.util.List;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 앨범 열기(따로 올리기), 기록 담기(사진 붙이기), 이름·지역 바꾸기(T4).
 *
 * <p>앨범을 바꾸는 요청은 모두 그룹 행을 먼저 잠근다(TravelGroupRepository.findByIdForUpdate). 그룹을 바꾸는 쿼리끼리
 * 잠금 순서를 맞추고, 나중에 나가기(T3)와 겨뤄도 나간 뒤에 담기는 일이 없게 한다. 그래서 멤버 확인도 GroupMembership 대신
 * 잠근 그룹으로 한다. 트랜잭션에서 그룹을 처음 읽는 게 잠금 조회여야 한다.
 *
 * <p>남의 기록이거나 없는 기록이면 여행 기록과 같은 TRAVEL_RECORD_NOT_FOUND다. 남의 기록이 있다는 것도 알려 주지 않는다.
 */
@Service
public class AlbumService {

    private final AlbumRepository albumRepository;
    private final TravelGroupRepository travelGroupRepository;
    private final RegionRepository regionRepository;
    private final SocialMemberGuard socialMemberGuard;
    private final TravelRecordQuery travelRecordQuery;
    private final AlbumQueryService albumQueryService;
    private final TransactionTemplate transactionTemplate;
    private final ApplicationEventPublisher eventPublisher;

    public AlbumService(AlbumRepository albumRepository,
                        TravelGroupRepository travelGroupRepository,
                        RegionRepository regionRepository,
                        SocialMemberGuard socialMemberGuard,
                        TravelRecordQuery travelRecordQuery,
                        AlbumQueryService albumQueryService,
                        PlatformTransactionManager transactionManager,
                        ApplicationEventPublisher eventPublisher) {
        this.albumRepository = albumRepository;
        this.travelGroupRepository = travelGroupRepository;
        this.regionRepository = regionRepository;
        this.socialMemberGuard = socialMemberGuard;
        this.travelRecordQuery = travelRecordQuery;
        this.albumQueryService = albumQueryService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.eventPublisher = eventPublisher;
    }

    /**
     * 내 기록 하나로 새 앨범을 연다. 지역을 보내지 않으면 그 기록의 지역이다.
     */
    @Transactional
    public AlbumDetail open(Long memberId, Long groupId, Long travelRecordId, String name, Long regionId) {
        socialMemberGuard.require(memberId);
        TravelGroup group = lockGroupOf(groupId, memberId);
        RecordBrief record = findOwnedRecord(travelRecordId, memberId);
        Region region = findRegion(regionId == null ? record.regionId() : regionId);
        String albumName = Album.nameFor(name, record.title(), region);
        Album album = albumRepository.save(Album.open(groupId, albumName, region, memberId, record.id()));
        publishUpdated(group, album, memberId);
        return albumQueryService.detailOf(album, memberId);
    }

    /**
     * 같은 기록을 동시에 담으면 그룹 행 잠금으로 차례로 처리하지만, 늦은 쪽은 잠금을 기다리기 전에 읽은
     * 옛 스냅샷으로 앨범을 보고 담으려다 유니크 제약(uk_album_entry_record)에 걸릴 수 있다.
     * 그 트랜잭션은 롤백하고, 다시 들어오면 이미 담긴 기록이라 다시 담은 것으로(created=false) 응답한다.
     * 그래서 이 메서드는 트랜잭션을 직접 연다(GroupInvitationService.accept와 같다).
     */
    public AlbumEntryResult addEntry(Long memberId, Long groupId, Long albumId, Long travelRecordId) {
        try {
            return transactionTemplate.execute(status -> add(memberId, groupId, albumId, travelRecordId));
        } catch (DataIntegrityViolationException exception) {
            return transactionTemplate.execute(status -> add(memberId, groupId, albumId, travelRecordId));
        }
    }

    /**
     * 보낸 값만 바꾼다. 둘 다 보내지 않으면 400이다.
     */
    @Transactional
    public AlbumDetail update(Long memberId, Long groupId, Long albumId, String name, Long regionId) {
        if (name == null && regionId == null) {
            throw new BusinessException(GroupErrorCode.EMPTY_ALBUM_UPDATE);
        }
        socialMemberGuard.require(memberId);
        lockGroupOf(groupId, memberId);
        Album album = findAlbum(albumId, groupId);
        if (name != null) {
            album.rename(name);
        }
        if (regionId != null) {
            album.changeRegion(findRegion(regionId));
        }
        return albumQueryService.detailOf(album, memberId);
    }

    private AlbumEntryResult add(Long memberId, Long groupId, Long albumId, Long travelRecordId) {
        socialMemberGuard.require(memberId);
        TravelGroup group = lockGroupOf(groupId, memberId);
        Album album = findAlbum(albumId, groupId);
        RecordBrief record = findOwnedRecord(travelRecordId, memberId);
        boolean created = album.addEntry(memberId, record.id());
        // 같은 기록을 다시 담았으면 알리지 않는다. 재시도로 롤백된 트랜잭션의 이벤트는 커밋되지 않아 버려진다.
        if (created) {
            publishUpdated(group, album, memberId);
        }
        // 새 항목의 담은 시각(created_at)은 저장할 때 정해진다. 응답에 넣으려고 여기서 저장한다.
        albumRepository.flush();
        return new AlbumEntryResult(albumQueryService.detailOf(album, memberId), created);
    }

    private TravelGroup lockGroupOf(Long groupId, Long memberId) {
        return travelGroupRepository.findByIdForUpdate(groupId)
                .filter(group -> group.hasMember(memberId))
                .orElseThrow(() -> new BusinessException(GroupErrorCode.GROUP_NOT_FOUND));
    }

    // 알림은 커밋된 뒤 따로 보낸다(ADR 0022 결정 7). 받을 멤버는 그룹 잠금 아래에서 정한다.
    private void publishUpdated(TravelGroup group, Album album, Long actorMemberId) {
        List<Long> recipients = group.getMembers().stream()
                .map(GroupMember::getMemberId)
                .filter(memberId -> !memberId.equals(actorMemberId))
                .toList();
        eventPublisher.publishEvent(new AlbumUpdated(
                group.getId(), group.getName(), album.getId(), album.getName(), actorMemberId, recipients));
    }

    private Album findAlbum(Long albumId, Long groupId) {
        return albumRepository.findByIdAndGroupId(albumId, groupId)
                .orElseThrow(() -> new BusinessException(GroupErrorCode.ALBUM_NOT_FOUND));
    }

    private RecordBrief findOwnedRecord(Long travelRecordId, Long memberId) {
        return travelRecordQuery.findOwned(travelRecordId, memberId)
                .orElseThrow(() -> new BusinessException(TravelRecordErrorCode.TRAVEL_RECORD_NOT_FOUND));
    }

    private Region findRegion(Long regionId) {
        return regionRepository.findById(regionId)
                .orElseThrow(() -> new BusinessException(RegionErrorCode.REGION_NOT_FOUND));
    }
}
