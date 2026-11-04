package com.mapmory.backend.notification.event;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.mapmory.backend.member.AuthProvider;
import com.mapmory.backend.member.event.GuestAbandoned;
import com.mapmory.backend.member.event.MemberWithdrawn;
import com.mapmory.backend.notification.application.PushTokenService;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class InactiveMemberListenerTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 4, 3, 0);

    private final PushTokenService pushTokenService = mock(PushTokenService.class);
    private final InactiveMemberListener listener = new InactiveMemberListener(pushTokenService);

    @Test
    void 탈퇴_뒤_기기_토큰_정리가_실패해도_예외를_올리지_않는다() {
        willThrow(new IllegalStateException("db")).given(pushTokenService).removeAllOf(1L);

        assertThatCode(() -> listener.onMemberWithdrawn(new MemberWithdrawn(1L, AuthProvider.KAKAO, NOW)))
                .doesNotThrowAnyException();

        verify(pushTokenService).removeAllOf(1L);
    }

    @Test
    void 버려진_뒤_기기_토큰_정리가_실패해도_예외를_올리지_않는다() {
        willThrow(new IllegalStateException("db")).given(pushTokenService).removeAllOf(2L);

        assertThatCode(() -> listener.onGuestAbandoned(new GuestAbandoned(2L, NOW)))
                .doesNotThrowAnyException();

        verify(pushTokenService).removeAllOf(2L);
    }
}
