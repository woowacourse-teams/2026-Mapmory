package com.mapmory.backend.group.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.mapmory.backend.group.domain.TravelGroupRepository;
import com.mapmory.backend.member.api.MemberQuery;
import java.util.List;
import java.util.Set;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;

class InactiveMemberGroupCleanupTest {

    private final GroupLeaveService groupLeaveService = mock(GroupLeaveService.class);
    private final TravelGroupRepository travelGroupRepository = mock(TravelGroupRepository.class);
    private final MemberQuery memberQuery = mock(MemberQuery.class);
    private final InactiveMemberGroupCleanup cleanup =
            new InactiveMemberGroupCleanup(groupLeaveService, travelGroupRepository, memberQuery);

    @Test
    void 한_그룹에서_실패해도_나머지_그룹에서_나간다() {
        given(groupLeaveService.groupIdsOf(1L)).willReturn(List.of(10L, 20L));
        willThrow(new IllegalStateException("db")).given(groupLeaveService).leaveAsInactive(1L, 10L);

        cleanup.leaveAllGroups(1L);

        verify(groupLeaveService).leaveAsInactive(1L, 20L);
    }

    @Test
    void 한_차례가_가득_차면_마지막_회원_다음부터_이어서_본다() {
        List<Long> fullBatch = LongStream.rangeClosed(1, InactiveMemberGroupCleanup.BATCH_SIZE).boxed().toList();
        long last = InactiveMemberGroupCleanup.BATCH_SIZE;
        given(travelGroupRepository.findMemberIdsAfter(eq(0L), any())).willReturn(fullBatch);
        given(travelGroupRepository.findMemberIdsAfter(eq(last), any())).willReturn(List.of(last + 1));
        given(memberQuery.inactiveAmong(fullBatch)).willReturn(Set.of(1L));
        given(memberQuery.inactiveAmong(List.of(last + 1))).willReturn(Set.of(last + 1));
        given(groupLeaveService.groupIdsOf(1L)).willReturn(List.of(10L));
        given(groupLeaveService.groupIdsOf(last + 1)).willReturn(List.of(20L));

        assertThat(cleanup.run()).isEqualTo(2);

        verify(groupLeaveService).leaveAsInactive(1L, 10L);
        verify(groupLeaveService).leaveAsInactive(last + 1, 20L);
    }

    @Test
    void 활동_중인_멤버는_나가게_하지_않는다() {
        given(travelGroupRepository.findMemberIdsAfter(eq(0L), any())).willReturn(List.of(1L));
        given(memberQuery.inactiveAmong(List.of(1L))).willReturn(Set.of());

        assertThat(cleanup.run()).isZero();

        verify(groupLeaveService, never()).groupIdsOf(any());
    }
}
