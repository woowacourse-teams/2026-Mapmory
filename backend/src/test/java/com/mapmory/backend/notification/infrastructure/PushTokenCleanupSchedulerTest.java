package com.mapmory.backend.notification.infrastructure;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.mapmory.backend.notification.application.InactivePushTokenCleanup;
import org.junit.jupiter.api.Test;

class PushTokenCleanupSchedulerTest {

    @Test
    void 정리가_실패해도_예외를_올리지_않고_다음_날_다시_돈다() {
        InactivePushTokenCleanup cleanup = mock(InactivePushTokenCleanup.class);
        given(cleanup.run()).willThrow(new IllegalStateException("db down"));

        assertThatCode(() -> new PushTokenCleanupScheduler(cleanup).cleanUp()).doesNotThrowAnyException();
    }
}
