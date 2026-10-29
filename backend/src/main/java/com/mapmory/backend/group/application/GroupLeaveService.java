package com.mapmory.backend.group.application;

import com.mapmory.backend.common.exception.BusinessException;
import com.mapmory.backend.group.domain.Album;
import com.mapmory.backend.group.domain.AlbumRepository;
import com.mapmory.backend.group.domain.GroupInvitationRepository;
import com.mapmory.backend.group.domain.TravelGroup;
import com.mapmory.backend.group.domain.TravelGroupRepository;
import com.mapmory.backend.group.exception.GroupErrorCode;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 그룹 나가기(T3). 내가 담은 앨범 항목을 모두 내리고, 마지막 멤버면 그룹·앨범·초대를 지운다(ADR 0022 결정 6).
 *
 * <p>그룹 행 잠금을 첫 문장으로 둔다. 잠금보다 먼저 일반 SELECT를 하면 스냅샷이 잠금 전에 정해져,
 * 그사이 담긴 항목이나 들어온 멤버를 못 보고 그룹을 지울 수 있다.
 *
 * <p>탈퇴한 회원도 같은 규칙으로 모든 그룹에서 나간다(#547). 탈퇴한 회원은 소셜 회원 확인을 통과하지 못하므로 확인 없이 나간다.
 */
@Service
public class GroupLeaveService {

    private final TravelGroupRepository travelGroupRepository;
    private final AlbumRepository albumRepository;
    private final GroupInvitationRepository groupInvitationRepository;
    private final SocialMemberGuard socialMemberGuard;

    public GroupLeaveService(TravelGroupRepository travelGroupRepository,
                             AlbumRepository albumRepository,
                             GroupInvitationRepository groupInvitationRepository,
                             SocialMemberGuard socialMemberGuard) {
        this.travelGroupRepository = travelGroupRepository;
        this.albumRepository = albumRepository;
        this.groupInvitationRepository = groupInvitationRepository;
        this.socialMemberGuard = socialMemberGuard;
    }

    @Transactional
    public void leave(Long memberId, Long groupId) {
        TravelGroup group = travelGroupRepository.findByIdForUpdate(groupId)
                .orElseThrow(() -> new BusinessException(GroupErrorCode.GROUP_NOT_FOUND));
        socialMemberGuard.require(memberId);
        if (!group.hasMember(memberId)) {
            throw new BusinessException(GroupErrorCode.GROUP_NOT_FOUND);
        }

        removeMember(group, memberId);
    }

    /**
     * 탈퇴한 회원이 속한 그룹. 탈퇴가 커밋된 뒤 불리므로 새 트랜잭션에서 읽는다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public List<Long> groupIdsOf(Long memberId) {
        return travelGroupRepository.findGroupIdsByMemberId(memberId);
    }

    /**
     * 탈퇴한 회원을 그룹에서 뺀다. 그룹마다 따로 커밋해 한 그룹이 실패해도 다른 그룹은 나간다.
     * 그사이 그룹이 지워졌거나 이미 나갔으면 아무것도 하지 않는다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void leaveAsWithdrawn(Long memberId, Long groupId) {
        travelGroupRepository.findByIdForUpdate(groupId)
                .filter(group -> group.hasMember(memberId))
                .ifPresent(group -> removeMember(group, memberId));
    }

    private void removeMember(TravelGroup group, Long memberId) {
        for (Album album : albumRepository.findAllByGroupIdContainingMember(group.getId(), memberId)) {
            album.removeEntriesBy(memberId);
            if (album.isEmpty()) {
                albumRepository.delete(album);
            }
        }

        if (group.isOnlyMember(memberId)) {
            deleteGroup(group);
            return;
        }
        group.leave(memberId);
    }

    // 마지막 멤버의 항목을 뗐으므로 남은 앨범은 보통 없다. 앨범과 초대는 그룹과 다른 애그리거트라 명시적으로 지운다.
    private void deleteGroup(TravelGroup group) {
        albumRepository.deleteAll(albumRepository.findAllByGroupId(group.getId()));
        groupInvitationRepository.deleteAllByGroupIdInBulk(group.getId());
        travelGroupRepository.delete(group);
    }
}
