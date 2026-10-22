package com.mapmory.shared.data.settings

import com.mapmory.shared.domain.model.ShareVideoStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ShareVideoJobStoreTest {
    @Test
    fun tracksJobStatusAndCompletionNotificationWithoutClearingTheJob() {
        val store = MemoryShareVideoJobStore()
        store.saveJobId(15L)
        store.updateStatus(ShareVideoStatus.RENDERING)

        assertEquals(15L, store.loadJobId())
        assertEquals(ShareVideoStatus.RENDERING, store.state.value.status)
        assertFalse(store.hasCompletionBeenNotified(15L))

        store.updateStatus(ShareVideoStatus.COMPLETED)
        store.markCompletionNotified(15L)

        assertEquals(15L, store.loadJobId())
        assertEquals(ShareVideoStatus.COMPLETED, store.state.value.status)
        assertTrue(store.hasCompletionBeenNotified(15L))

        store.clear()
        assertNull(store.loadJobId())
        assertNull(store.state.value.status)
    }
}
