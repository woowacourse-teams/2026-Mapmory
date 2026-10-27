package com.mapmory.backend.group.application;

import com.mapmory.backend.common.exception.BusinessException;
import com.mapmory.backend.group.domain.TravelGroup;
import com.mapmory.backend.group.domain.TravelGroupRepository;
import com.mapmory.backend.group.exception.GroupErrorCode;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 그룹 경로에 들어온 회원이 그 그룹 멤버인지 확인한다. 그룹·초대·앨범 서비스가 맨 먼저 부른다.
 *
 * <p>그룹이 없을 때와 멤버가 아닐 때를 구분하지 않고 같은 404를 준다. 남의 그룹이 있는지 알려 주지 않기 위해서다.
 * 그룹 객체가 필요 없으면 {@link #validateMember}, 필요하면 {@link #findGroupOf}를 쓴다. 멤버 확인은 이 클래스에서만 한다.
 */
@Component
public class GroupMembership {

    // 한 회원이 속할 수 있는 그룹 수. 만든 수가 아니라 속한 수로 센다(ADR 0022 결정 6).
    static final int MAX_GROUPS_PER_MEMBER = 10;

    private final TravelGroupRepository travelGroupRepository;

    public GroupMembership(TravelGroupRepository travelGroupRepository) {
        this.travelGroupRepository = travelGroupRepository;
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
     * <p>같은 사람이 동시에 만들거나 수락하면 한두 개 넘을 수 있다. 자기 요청끼리의 경쟁이라 잠금을 더 걸지 않는다.
     */
    @Transactional(readOnly = true)
    public void validateCanJoinAnother(Long memberId) {
        if (travelGroupRepository.countGroupsOf(memberId) >= MAX_GROUPS_PER_MEMBER) {
            throw new BusinessException(GroupErrorCode.TOO_MANY_GROUPS);
        }
    }

    /**
     * 멤버인 그룹을 불러온다. 부르는 쪽 트랜잭션 안에서 돌려준 그룹을 바꿀 수 있다.
     */
    @Transactional(readOnly = true)
    public TravelGroup findGroupOf(Long groupId, Long memberId) {
        return travelGroupRepository.findById(groupId)
                .filter(group -> group.hasMember(memberId))
                .orElseThrow(() -> new BusinessException(GroupErrorCode.GROUP_NOT_FOUND));
    }
}
