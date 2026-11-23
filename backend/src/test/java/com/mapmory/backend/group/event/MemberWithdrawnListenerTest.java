package com.mapmory.backend.group.event;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.mapmory.backend.group.application.InactiveMemberGroupCleanup;
import com.mapmory.backend.member.AuthProvider;
import com.mapmory.backend.member.event.MemberWithdrawn;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class MemberWithdrawnListenerTest {

    private final InactiveMemberGroupCleanup cleanup = mock(InactiveMemberGroupCleanup.class);
    private final MemberWithdrawnListener listener = new MemberWithdrawnListener(cleanup);

    @Test
    void 그룹_나가기가_실패해도_탈퇴에_예외를_올리지_않는다() {
        willThrow(new IllegalStateException("db")).given(cleanup).leaveAllGroups(1L);

        assertThatCode(() -> listener.onMemberWithdrawn(
                new MemberWithdrawn(1L, AuthProvider.KAKAO, LocalDateTime.of(2026, 11, 5, 3, 0))))
                .doesNotThrowAnyException();

        verify(cleanup).leaveAllGroups(1L);
    }
}
