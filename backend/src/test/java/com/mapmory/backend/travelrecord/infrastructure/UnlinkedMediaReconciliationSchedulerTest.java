package com.mapmory.backend.travelrecord.infrastructure;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.mapmory.backend.travelrecord.application.UnlinkedMediaReconciliation;
import org.junit.jupiter.api.Test;

class UnlinkedMediaReconciliationSchedulerTest {

    @Test
    void 대조가_실패해도_예외를_올리지_않고_다음_날_다시_돈다() {
        UnlinkedMediaReconciliation reconciliation = mock(UnlinkedMediaReconciliation.class);
        given(reconciliation.run()).willThrow(new IllegalStateException("s3 down"));

        assertThatCode(() -> new UnlinkedMediaReconciliationScheduler(reconciliation).reconcile())
                .doesNotThrowAnyException();
    }
}
