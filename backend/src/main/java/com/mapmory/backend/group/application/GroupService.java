package com.mapmory.backend.group.application;

import com.mapmory.backend.group.domain.GroupMember;
import com.mapmory.backend.group.domain.GroupSummary;
import com.mapmory.backend.group.domain.TravelGroup;
import com.mapmory.backend.group.domain.TravelGroupRepository;
import com.mapmory.backend.member.api.MemberQuery;
import com.mapmory.backend.recordmedia.ExpiringUrl;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 그룹 만들기, 내 그룹 목록, 그룹 조회, 이름 바꾸기(T1).
 *
 * <p>그룹 API는 모두 소셜 로그인 회원만 쓴다. 그룹 밖 회원에게는 그룹이 있어도 GROUP_NOT_FOUND를 준다.
 * 멤버 닉네임은 회원에서 id로 조회만 한다(ADR 0022 결정 5).
 */
@Service
public class GroupService {

    private final TravelGroupRepository travelGroupRepository;
    private final MemberQuery memberQuery;
    private final SocialMemberGuard socialMemberGuard;
    private final GroupMembership groupMembership;
    private final AlbumQueryService albumQueryService;
    private final Clock clock;

    public GroupService(TravelGroupRepository travelGroupRepository, MemberQuery memberQuery,
                        SocialMemberGuard socialMemberGuard, GroupMembership groupMembership,
                        AlbumQueryService albumQueryService, Clock clock) {
        this.travelGroupRepository = travelGroupRepository;
        this.memberQuery = memberQuery;
        this.socialMemberGuard = socialMemberGuard;
        this.groupMembership = groupMembership;
        this.albumQueryService = albumQueryService;
        this.clock = clock;
    }

    /**
     * 회원당 그룹 수는 센 다음 넣으므로 같은 회원의 요청이 겹치면 상한을 넘는다. 회원 행을 먼저 잠가 차례로 처리한다.
     */
    @Transactional
    public GroupDetail create(Long memberId, String name) {
        socialMemberGuard.lock(memberId);
        groupMembership.validateCanJoinAnother(memberId);
        TravelGroup group = TravelGroup.create(name, memberId, LocalDateTime.now(clock));
        return toDetail(travelGroupRepository.save(group), memberId);
    }

    @Transactional(readOnly = true)
    public List<GroupCard> findMine(Long memberId) {
        socialMemberGuard.require(memberId);
        List<GroupSummary> summaries = travelGroupRepository.findSummariesByMemberId(memberId);
        Map<Long, ExpiringUrl> covers = albumQueryService.latestCoversOf(
                summaries.stream().map(GroupSummary::id).toList());
        return summaries.stream()
                .map(summary -> new GroupCard(summary, covers.get(summary.id())))
                .toList();
    }

    @Transactional(readOnly = true)
    public GroupDetail get(Long memberId, Long groupId) {
        socialMemberGuard.require(memberId);
        return toDetail(groupMembership.findGroupOf(groupId, memberId), memberId);
    }

    /**
     * 그룹 행을 잠그고 바꾼다. 그룹을 바꾸는 다른 흐름(수락·나가기·앨범)과 같이 그룹 행을 먼저 잠근다.
     * 소셜 회원 확인도 잠금 뒤에 {@link GroupMembership#lockGroupOf}가 한다.
     */
    @Transactional
    public GroupDetail rename(Long memberId, Long groupId, String name) {
        TravelGroup group = groupMembership.lockGroupOf(groupId, memberId);
        group.rename(name);
        return toDetail(group, memberId);
    }

    private GroupDetail toDetail(TravelGroup group, Long myMemberId) {
        List<GroupMember> members = group.getMembers();
        Map<Long, String> nicknames = memberQuery.nicknamesOf(
                members.stream().map(GroupMember::getMemberId).toList());
        List<GroupDetail.MemberView> views = members.stream()
                .map(member -> new GroupDetail.MemberView(
                        member.getMemberId(),
                        nicknames.get(member.getMemberId()),
                        member.getMemberId().equals(group.getOwnerMemberId()),
                        member.getMemberId().equals(myMemberId),
                        member.getJoinedAt()))
                .toList();
        return new GroupDetail(group.getId(), group.getName(), views);
    }
}
