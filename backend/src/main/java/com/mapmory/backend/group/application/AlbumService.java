package com.mapmory.backend.group.application;

import com.mapmory.backend.common.exception.BusinessException;
import com.mapmory.backend.group.api.AlbumUpdated;
import com.mapmory.backend.group.domain.Album;
import com.mapmory.backend.group.domain.AlbumRepository;
import com.mapmory.backend.group.domain.TravelGroup;
import com.mapmory.backend.group.domain.TravelGroupRepository;
import com.mapmory.backend.group.exception.GroupErrorCode;
import com.mapmory.backend.region.domain.Region;
import com.mapmory.backend.region.domain.RegionRepository;
import com.mapmory.backend.region.exception.RegionErrorCode;
import com.mapmory.backend.travelrecord.api.RecordBrief;
import com.mapmory.backend.travelrecord.api.TravelRecordQuery;
import com.mapmory.backend.travelrecord.exception.TravelRecordErrorCode;
import java.util.Optional;
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
 * 잠금 순서를 맞추고, 나중에 나가기(T3)와 겨뤄도 나간 뒤에 열거나 담는 일이 없게 한다. 그래서 멤버 확인도 GroupMembership 대신
 * 잠근 그룹으로 한다({@link #lockGroupOf}).
 *
 * <p>그룹 잠금이 트랜잭션의 첫 조회여야 한다. REPEATABLE READ의 스냅샷은 첫 일반 조회 때 DB 전체를 기준으로 생긴다.
 * 회원 확인 같은 일반 조회를 먼저 하면 스냅샷이 잠금을 기다리기 전에 생겨, 잠금 뒤에 일반 조회로 읽는 멤버 목록과 앨범이 기다리기 전
 * 상태가 된다. 그러면 그사이 나간 사람이 멤버로 보여 앨범을 열거나 담고, 그사이 바뀐 앨범 이름·지역을 옛 값으로 되돌린다.
 *
 * <p>앨범을 열거나 담으면 회원을 가리키는 행(album.opener_member_id, album_entry.member_id)을 넣어 FK 확인이 회원 행을
 * 공유 잠금한다. 그룹 흐름은 모두 그룹 → 회원 순서다(초대 수락의 멤버 INSERT, 나가기의 방장·앨범 연 사람 넘기기도 같다).
 * 회원 행을 먼저 잠그면 이 순서와 반대가 되어 나가기와 데드락이 나므로 회원은 잠그지 않는다(ADR 0028).
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
        TravelGroup group = lockGroupOf(groupId, memberId);
        RecordBrief record = findOwnedRecord(travelRecordId, memberId);
        Region region = findRegion(regionId == null ? record.regionId() : regionId);
        String albumName = Album.nameFor(name, record.title(), region);
        Album album = albumRepository.save(Album.open(groupId, albumName, region, memberId, record.id()));
        publishUpdated(group, album, memberId);
        return albumQueryService.detailOf(album, memberId);
    }

    /**
     * 같은 기록을 동시에 담으면 그룹 행 잠금으로 차례로 처리한다. 잠금이 첫 조회라 늦은 쪽은 앞 사람이 담은 항목을 보고
     * 다시 담은 것으로(created=false) 응답한다. 그래도 유니크 제약(uk_album_entry_record)에 걸리면 그 트랜잭션은 롤백하고
     * 한 번 더 들어와 같은 응답을 준다. 그래서 이 메서드는 트랜잭션을 직접 연다(GroupInvitationService.accept와 같다).
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

    // 그룹을 잠근 뒤에 소셜 회원인지 확인한다. 회원 확인은 일반 조회라 잠금보다 먼저 하면 스냅샷이 잠금 전에 생긴다.
    // 오류는 회원 쪽이 먼저다(게스트 403, 없거나 탈퇴한 회원 401). 그다음에 그룹이 없거나 멤버가 아니면 404다.
    private TravelGroup lockGroupOf(Long groupId, Long memberId) {
        Optional<TravelGroup> group = travelGroupRepository.findByIdForUpdate(groupId)
                .filter(found -> found.hasMember(memberId));
        socialMemberGuard.require(memberId);
        return group.orElseThrow(() -> new BusinessException(GroupErrorCode.GROUP_NOT_FOUND));
    }

    // 알림은 커밋된 뒤 따로 보낸다(ADR 0022 결정 7). 받을 멤버는 그룹 잠금 아래에서 정한다.
    private void publishUpdated(TravelGroup group, Album album, Long actorMemberId) {
        eventPublisher.publishEvent(new AlbumUpdated(group.getId(), group.getName(), album.getId(), album.getName(),
                actorMemberId, group.otherMemberIds(actorMemberId)));
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
