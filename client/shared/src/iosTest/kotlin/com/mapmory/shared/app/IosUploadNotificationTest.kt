package com.mapmory.shared.app

import platform.UIKit.UIApplicationState.UIApplicationStateActive
import platform.UIKit.UIApplicationState.UIApplicationStateInactive
import platform.UIKit.UIApplicationState.UIApplicationStateBackground
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertEquals
import com.mapmory.shared.domain.model.TripRecordDraft
import platform.Foundation.NSUUID

class IosUploadNotificationTest {
    @Test
    fun pendingUploadAndUncertainServerSaveSurviveStoreRecreation() {
        val namespace = "upload-lifecycle-test-${NSUUID().UUIDString}"
        val store = IosBackgroundSaveStore(namespace)
        val draft = TripRecordDraft(locationId = 1, startDate = "2026-11-05", mediaObjectKeys = emptyList(), title = "복구 확인")
        val pending = listOf(
            PendingTripRecordSave(1, "서울", draft),
            PendingTripRecordSave(2, "서울", draft, serverSaveStarted = true),
        )
        try {
            store.write(pending)
            assertEquals(pending, IosBackgroundSaveStore(namespace).read())
        } finally {
            store.write(emptyList())
        }
    }

    @Test
    fun systemNotificationOnlyWhenAppHasActuallyEnteredBackground() {
        assertFalse(shouldSendUploadNotification(UIApplicationStateActive))
        assertFalse(shouldSendUploadNotification(UIApplicationStateInactive))
        assertTrue(shouldSendUploadNotification(UIApplicationStateBackground))
    }
}
