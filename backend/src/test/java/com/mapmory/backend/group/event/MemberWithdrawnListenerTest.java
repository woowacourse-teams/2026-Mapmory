package com.mapmory.backend.group.event;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.mapmory.backend.group.application.GroupLeaveService;
import com.mapmory.backend.member.AuthProvider;
import com.mapmory.backend.member.event.MemberWithdrawn;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class MemberWithdrawnListenerTest {

    private static final MemberWithdrawn WITHDRAWN =
            new MemberWithdrawn(1L, AuthProvider.KAKAO, LocalDateTime.of(2026, 10, 29, 3, 0));

    private final GroupLeaveService groupLeaveService = mock(GroupLeaveService.class);
    private final MemberWithdrawnListener listener = new MemberWithdrawnListener(groupLeaveService);

    @Test
    void 한_그룹에서_실패해도_나머지_그룹에서는_나간다() {
        given(groupLeaveService.groupIdsOf(1L)).willReturn(List.of(10L, 20L));
        willThrow(new IllegalStateException("lock timeout")).given(groupLeaveService).leaveAsWithdrawn(1L, 10L);

        assertThatCode(() -> listener.onMemberWithdrawn(WITHDRAWN)).doesNotThrowAnyException();

        verify(groupLeaveService).leaveAsWithdrawn(1L, 20L);
    }

    @Test
    void 그룹을_읽다가_실패해도_탈퇴에_예외를_올리지_않는다() {
        willThrow(new IllegalStateException("db down")).given(groupLeaveService).groupIdsOf(1L);

        assertThatCode(() -> listener.onMemberWithdrawn(WITHDRAWN)).doesNotThrowAnyException();
    }
}
