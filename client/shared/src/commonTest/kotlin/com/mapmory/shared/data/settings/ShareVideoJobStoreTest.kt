package com.mapmory.shared.data.settings

import com.mapmory.shared.domain.model.ShareVideoStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ShareVideoJobStoreTest {
    @Test
    fun statusNetworkFailureDoesNotAllowAnotherGenerationRequest() {
        for (status in listOf(null, ShareVideoStatus.PENDING, ShareVideoStatus.RENDERING, ShareVideoStatus.COMPLETED)) {
            val store = MemoryShareVideoJobStore()
            store.saveJobId(15L, startedAtEpochMillis = 100L)
            status?.let(store::updateStatus)

            store.updateStatusCheckError("네트워크 연결을 확인해 주세요")
            assertFalse(store.tryStartCreationRequest(), "A status lookup failure must not duplicate job $status")
            store.retryStatusCheck()
            assertFalse(store.tryStartCreationRequest())
            assertEquals(15L, store.loadJobId())
            assertEquals(status, store.state.value.status)
        }
    }

    @Test
    fun preventsDuplicateCreationUntilTheCurrentJobFails() {
        val store = MemoryShareVideoJobStore()

        assertTrue(store.tryStartCreationRequest())
        assertFalse(store.tryStartCreationRequest())
        store.finishCreationRequest()
        store.saveJobId(15L, startedAtEpochMillis = 1_792_752_000_000L)
        assertFalse(store.tryStartCreationRequest())

        store.updateStatus(ShareVideoStatus.FAILED)
        assertTrue(store.tryStartCreationRequest())
    }

    @Test
    fun manualStatusRetryIncrementsTheMonitorTrigger() {
        val store = MemoryShareVideoJobStore()
        store.saveJobId(15L, startedAtEpochMillis = 1_792_752_000_000L)
        store.updateStatusCheckError("일시적인 오류")

        store.retryStatusCheck()

        assertEquals(1, store.state.value.statusCheckRetryCount)
        assertNull(store.state.value.statusCheckError)
        assertEquals(15L, store.loadJobId())
    }

    @Test
    fun tracksJobStatusAndCompletionNotificationWithoutClearingTheJob() {
        val store = MemoryShareVideoJobStore()
        store.saveJobId(15L, startedAtEpochMillis = 1_792_752_000_000L)
        store.updateStatus(ShareVideoStatus.RENDERING)

        assertEquals(15L, store.loadJobId())
        assertEquals(ShareVideoStatus.RENDERING, store.state.value.status)
        assertEquals(1_792_752_000_000L, store.state.value.startedAtEpochMillis)
        assertFalse(store.hasCompletionBeenNotified(15L))

        store.updateStatus(ShareVideoStatus.COMPLETED)
        store.markCompletionNotified(15L)

        assertEquals(15L, store.loadJobId())
        assertEquals(ShareVideoStatus.COMPLETED, store.state.value.status)
        assertTrue(store.hasCompletionBeenNotified(15L))

        store.clear()
        assertNull(store.loadJobId())
        assertNull(store.state.value.status)
        assertNull(store.state.value.startedAtEpochMillis)
    }
}
