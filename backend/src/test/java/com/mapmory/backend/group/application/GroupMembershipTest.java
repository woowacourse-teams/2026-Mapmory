package com.mapmory.backend.group.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.mapmory.backend.common.exception.BusinessException;
import com.mapmory.backend.group.domain.TravelGroup;
import com.mapmory.backend.group.domain.TravelGroupRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import com.mapmory.backend.group.exception.GroupErrorCode;
import org.junit.jupiter.api.Test;

class GroupMembershipTest {

    private final TravelGroupRepository travelGroupRepository = mock(TravelGroupRepository.class);
    private final GroupMembership groupMembership = new GroupMembership(travelGroupRepository);

    @Test
    void 멤버면_통과한다() {
        when(travelGroupRepository.existsMember(10L, 1L)).thenReturn(true);

        assertThatCode(() -> groupMembership.validateMember(10L, 1L)).doesNotThrowAnyException();
    }

    @Test
    void 멤버가_아니면_그룹이_없다고_답한다() {
        when(travelGroupRepository.existsMember(10L, 2L)).thenReturn(false);

        assertThatThrownBy(() -> groupMembership.validateMember(10L, 2L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(GroupErrorCode.GROUP_NOT_FOUND);
    }

    @Test
    void 멤버면_그룹을_돌려준다() {
        TravelGroup group = TravelGroup.create("제주 가족", 1L, LocalDateTime.now());
        when(travelGroupRepository.findById(10L)).thenReturn(Optional.of(group));

        assertThat(groupMembership.findGroupOf(10L, 1L)).isSameAs(group);
    }

    @Test
    void 멤버가_아니면_그룹을_돌려주지_않고_그룹이_없다고_답한다() {
        TravelGroup group = TravelGroup.create("제주 가족", 1L, LocalDateTime.now());
        when(travelGroupRepository.findById(10L)).thenReturn(Optional.of(group));

        assertThatThrownBy(() -> groupMembership.findGroupOf(10L, 2L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(GroupErrorCode.GROUP_NOT_FOUND);
    }

    @Test
    void 그룹이_없으면_그룹이_없다고_답한다() {
        when(travelGroupRepository.findById(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> groupMembership.findGroupOf(10L, 1L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(GroupErrorCode.GROUP_NOT_FOUND);
    }
}
