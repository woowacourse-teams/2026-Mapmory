package com.mapmory.backend.group.infrastructure;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.mapmory.backend.group.application.InactiveMemberGroupCleanup;
import org.junit.jupiter.api.Test;

class InactiveMemberGroupCleanupSchedulerTest {

    @Test
    void 정리가_실패해도_예외를_올리지_않고_다음_날_다시_돈다() {
        InactiveMemberGroupCleanup cleanup = mock(InactiveMemberGroupCleanup.class);
        given(cleanup.run()).willThrow(new IllegalStateException("db down"));

        assertThatCode(() -> new InactiveMemberGroupCleanupScheduler(cleanup).cleanUp()).doesNotThrowAnyException();
    }
}
