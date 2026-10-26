package com.mapmory.backend.group.application;

import com.mapmory.backend.common.exception.BusinessException;
import com.mapmory.backend.group.domain.GroupInvitation;
import com.mapmory.backend.group.domain.GroupInvitationRepository;
import com.mapmory.backend.group.domain.TravelGroup;
import com.mapmory.backend.group.domain.TravelGroupRepository;
import com.mapmory.backend.group.exception.GroupErrorCode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 초대 만들기, 미리보기, 수락(T2).
 *
 * <p>초대 하나로 링크(토큰)와 직접 입력(6자리 코드)을 모두 받는다. 없거나 만료된 초대는 구분하지 않고 INVITATION_NOT_FOUND다.
 */
@Service
public class GroupInvitationService {

    // 코드가 이미 쓰이고 있으면 다시 뽑는다. 32^6(약 10억) 중 하나라 다섯 번 안에 거의 항상 끝난다.
    private static final int MAX_CODE_ATTEMPTS = 5;

    private final GroupInvitationRepository groupInvitationRepository;
    private final TravelGroupRepository travelGroupRepository;
    private final GroupMembership groupMembership;
    private final SocialMemberGuard socialMemberGuard;
    private final GroupService groupService;
    private final InvitationKeyGenerator invitationKeyGenerator;
    private final Clock clock;

    public GroupInvitationService(GroupInvitationRepository groupInvitationRepository,
                                  TravelGroupRepository travelGroupRepository,
                                  GroupMembership groupMembership,
                                  SocialMemberGuard socialMemberGuard,
                                  GroupService groupService,
                                  InvitationKeyGenerator invitationKeyGenerator,
                                  Clock clock) {
        this.groupInvitationRepository = groupInvitationRepository;
        this.travelGroupRepository = travelGroupRepository;
        this.groupMembership = groupMembership;
        this.socialMemberGuard = socialMemberGuard;
        this.groupService = groupService;
        this.invitationKeyGenerator = invitationKeyGenerator;
        this.clock = clock;
    }

    @Transactional
    public GroupInvitation create(Long memberId, Long groupId) {
        socialMemberGuard.require(memberId);
        groupMembership.validateMember(groupId, memberId);
        GroupInvitation invitation = GroupInvitation.issue(groupId, memberId, invitationKeyGenerator.token(),
                unusedCode(), LocalDateTime.now(clock));
        return groupInvitationRepository.save(invitation);
    }

    /**
     * 게스트도 부를 수 있다. 앱은 이 결과로 로그인을 권하는 화면을 그린다.
     */
    @Transactional(readOnly = true)
    public InvitationPreview preview(Long memberId, String tokenOrCode) {
        GroupInvitation invitation = findValid(tokenOrCode);
        TravelGroup group = travelGroupRepository.findById(invitation.getGroupId())
                .orElseThrow(() -> new BusinessException(GroupErrorCode.INVITATION_NOT_FOUND));
        return new InvitationPreview(group.getName(), group.getMembers().size(), group.hasMember(memberId));
    }

    /**
     * 이미 멤버면 아무것도 바꾸지 않고 그룹을 돌려준다.
     */
    @Transactional
    public GroupDetail accept(Long memberId, String tokenOrCode) {
        socialMemberGuard.require(memberId);
        GroupInvitation invitation = findValid(tokenOrCode);
        TravelGroup group = travelGroupRepository.findByIdForUpdate(invitation.getGroupId())
                .orElseThrow(() -> new BusinessException(GroupErrorCode.INVITATION_NOT_FOUND));
        group.join(memberId, LocalDateTime.now(clock));
        return groupService.get(memberId, group.getId());
    }

    private GroupInvitation findValid(String tokenOrCode) {
        return find(tokenOrCode)
                .filter(invitation -> !invitation.isExpired(LocalDateTime.now(clock)))
                .orElseThrow(() -> new BusinessException(GroupErrorCode.INVITATION_NOT_FOUND));
    }

    // 6자리면 코드, 아니면 링크 토큰이다. 코드는 대소문자를 가리지 않는다.
    private Optional<GroupInvitation> find(String tokenOrCode) {
        if (tokenOrCode.length() == InvitationKeyGenerator.CODE_LENGTH) {
            return groupInvitationRepository.findByCode(tokenOrCode.toUpperCase(Locale.ROOT));
        }
        return groupInvitationRepository.findByToken(tokenOrCode);
    }

    private String unusedCode() {
        for (int attempt = 0; attempt < MAX_CODE_ATTEMPTS; attempt++) {
            String code = invitationKeyGenerator.code();
            if (groupInvitationRepository.findByCode(code).isEmpty()) {
                return code;
            }
        }
        throw new IllegalStateException("초대 코드를 " + MAX_CODE_ATTEMPTS + "번 뽑았지만 모두 이미 쓰이고 있습니다.");
    }
}
