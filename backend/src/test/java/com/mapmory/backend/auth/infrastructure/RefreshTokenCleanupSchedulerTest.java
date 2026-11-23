package com.mapmory.backend.auth.infrastructure;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;

import com.mapmory.backend.auth.token.refresh.ExpiredRefreshTokenCleanup;
import org.junit.jupiter.api.Test;

class RefreshTokenCleanupSchedulerTest {

    @Test
    void 정리가_실패해도_예외를_올리지_않고_다음_날_다시_돈다() {
        ExpiredRefreshTokenCleanup cleanup = mock(ExpiredRefreshTokenCleanup.class);
        willThrow(new IllegalStateException("db down")).given(cleanup).run();

        assertThatCode(() -> new RefreshTokenCleanupScheduler(cleanup).cleanUp()).doesNotThrowAnyException();
    }
}
