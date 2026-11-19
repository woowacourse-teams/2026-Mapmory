package com.mapmory.backend.group.application;

import com.mapmory.backend.common.exception.BusinessException;
import com.mapmory.backend.group.domain.TravelGroup;
import com.mapmory.backend.group.domain.TravelGroupRepository;
import com.mapmory.backend.group.exception.GroupErrorCode;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 그룹 경로에 들어온 회원이 그 그룹 멤버인지 확인한다. 그룹·초대·앨범 서비스가 맨 먼저 부른다.
 *
 * <p>그룹이 없을 때와 멤버가 아닐 때를 구분하지 않고 같은 404를 준다. 남의 그룹이 있는지 알려 주지 않기 위해서다.
 * 그룹 객체가 필요 없으면 {@link #validateMember}, 읽기만 하면 {@link #findGroupOf}, 그룹을 바꾸면 {@link #lockGroupOf}를 쓴다.
 * 멤버 확인은 이 클래스에서만 한다.
 */
@Component
public class GroupMembership {

    // 한 회원이 속할 수 있는 그룹 수. 만든 수가 아니라 속한 수로 센다(ADR 0022 결정 6).
    static final int MAX_GROUPS_PER_MEMBER = 10;

    private final TravelGroupRepository travelGroupRepository;
    private final SocialMemberGuard socialMemberGuard;

    public GroupMembership(TravelGroupRepository travelGroupRepository, SocialMemberGuard socialMemberGuard) {
        this.travelGroupRepository = travelGroupRepository;
        this.socialMemberGuard = socialMemberGuard;
    }

    @Transactional(readOnly = true)
    public void validateMember(Long groupId, Long memberId) {
        if (!travelGroupRepository.existsMember(groupId, memberId)) {
            throw new BusinessException(GroupErrorCode.GROUP_NOT_FOUND);
        }
    }

    /**
     * 그룹을 하나 더 만들거나 들어갈 수 있는지 확인한다.
     *
     * <p>잠그지 않고 센 다음 넣으므로, 같은 회원이 동시에 만들거나 서로 다른 그룹에 수락하면 동시에 처리된 요청 수만큼 넘을 수 있다.
     * 자기 요청끼리의 경쟁이고 넘어도 그다음 요청부터 막힌다. 막으려고 회원 행을 잠그면 그룹 흐름의 잠금 순서가 뒤집혀
     * 나가기와 데드락이 나므로 잠그지 않는다(ADR 0022 결정 6, ADR 0028).
     */
    @Transactional(readOnly = true)
    public void validateCanJoinAnother(Long memberId) {
        if (travelGroupRepository.countGroupsOf(memberId) >= MAX_GROUPS_PER_MEMBER) {
            throw new BusinessException(GroupErrorCode.TOO_MANY_GROUPS);
        }
    }

    /**
     * 그룹을 잠그고(FOR UPDATE) 소셜 회원인지 확인한 뒤, 멤버인 그룹을 돌려준다. 그룹을 바꾸는 흐름은 이것으로 시작한다.
     *
     * <p>잠근 행은 REPEATABLE READ에서도 최신이다. 잠그지 않고 읽으면 읽은 뒤 커밋된 수락·나가기의 인원 수와 방장을 옛 값으로 되돌린다.
     * 이 잠금이 트랜잭션의 첫 조회여야 한다. 그룹을 먼저 일반 조회하면 1차 캐시의 옛 값을 돌려주고, 회원 확인 같은 다른 일반 조회를
     * 먼저 하면 스냅샷이 잠금을 기다리기 전에 생겨 잠금 뒤에 읽는 행(앨범 등)이 옛 상태가 된다. 그래서 회원 확인을 여기서 잠금 뒤에 한다.
     *
     * <p>오류는 회원 쪽이 먼저다. 게스트는 403, 없거나 탈퇴한 회원은 401이고, 그다음에 그룹이 없거나 멤버가 아니면 404다.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public TravelGroup lockGroupOf(Long groupId, Long memberId) {
        Optional<TravelGroup> group = travelGroupRepository.findByIdForUpdate(groupId)
                .filter(found -> found.hasMember(memberId));
        socialMemberGuard.require(memberId);
        return group.orElseThrow(() -> new BusinessException(GroupErrorCode.GROUP_NOT_FOUND));
    }

    /**
     * 멤버인 그룹을 읽기 위해 불러온다. 잠그지 않으므로 그룹을 바꾸는 데 쓰지 않는다({@link #lockGroupOf}).
     */
    @Transactional(readOnly = true)
    public TravelGroup findGroupOf(Long groupId, Long memberId) {
        return travelGroupRepository.findById(groupId)
                .filter(group -> group.hasMember(memberId))
                .orElseThrow(() -> new BusinessException(GroupErrorCode.GROUP_NOT_FOUND));
    }
}
