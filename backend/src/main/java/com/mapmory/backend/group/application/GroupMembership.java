package com.mapmory.backend.group.application;

import com.mapmory.backend.common.exception.BusinessException;
import com.mapmory.backend.group.domain.TravelGroupRepository;
import com.mapmory.backend.group.exception.GroupErrorCode;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 그룹 경로에 들어온 회원이 그 그룹 멤버인지 확인한다. 그룹·초대·앨범 서비스가 맨 먼저 부른다.
 *
 * <p>그룹이 없을 때와 멤버가 아닐 때를 구분하지 않고 같은 404를 준다. 남의 그룹이 있는지 알려 주지 않기 위해서다.
 */
@Component
public class GroupMembership {

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
}
