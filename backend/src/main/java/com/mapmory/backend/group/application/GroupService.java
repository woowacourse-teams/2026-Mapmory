package com.mapmory.backend.group.application;

import com.mapmory.backend.group.domain.GroupMember;
import com.mapmory.backend.group.domain.GroupSummary;
import com.mapmory.backend.group.domain.TravelGroup;
import com.mapmory.backend.group.domain.TravelGroupRepository;
import com.mapmory.backend.member.Member;
import com.mapmory.backend.member.MemberRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
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
    private final MemberRepository memberRepository;
    private final SocialMemberGuard socialMemberGuard;
    private final GroupMembership groupMembership;
    private final Clock clock;

    public GroupService(TravelGroupRepository travelGroupRepository, MemberRepository memberRepository,
                        SocialMemberGuard socialMemberGuard, GroupMembership groupMembership, Clock clock) {
        this.travelGroupRepository = travelGroupRepository;
        this.memberRepository = memberRepository;
        this.socialMemberGuard = socialMemberGuard;
        this.groupMembership = groupMembership;
        this.clock = clock;
    }

    @Transactional
    public GroupDetail create(Long memberId, String name) {
        socialMemberGuard.require(memberId);
        TravelGroup group = TravelGroup.create(name, memberId, LocalDateTime.now(clock));
        return toDetail(travelGroupRepository.save(group), memberId);
    }

    @Transactional(readOnly = true)
    public List<GroupSummary> findMine(Long memberId) {
        socialMemberGuard.require(memberId);
        return travelGroupRepository.findSummariesByMemberId(memberId);
    }

    @Transactional(readOnly = true)
    public GroupDetail get(Long memberId, Long groupId) {
        socialMemberGuard.require(memberId);
        return toDetail(groupMembership.findGroupOf(groupId, memberId), memberId);
    }

    @Transactional
    public GroupDetail rename(Long memberId, Long groupId, String name) {
        socialMemberGuard.require(memberId);
        TravelGroup group = groupMembership.findGroupOf(groupId, memberId);
        group.rename(name);
        return toDetail(group, memberId);
    }

    private GroupDetail toDetail(TravelGroup group, Long myMemberId) {
        List<GroupMember> members = group.getMembers();
        List<Long> memberIds = members.stream().map(GroupMember::getMemberId).toList();
        Map<Long, String> nicknames = memberRepository.findAllById(memberIds).stream()
                .collect(Collectors.toMap(Member::getId, Member::getNickname));
        List<GroupDetail.MemberView> views = memberIds.stream()
                .map(memberId -> new GroupDetail.MemberView(
                        nicknames.get(memberId),
                        memberId.equals(group.getOwnerMemberId()),
                        memberId.equals(myMemberId)))
                .toList();
        return new GroupDetail(group.getId(), group.getName(), views);
    }
}
