package com.mapmory.shared

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class IosPushNotificationTest {
    @AfterTest
    fun clearNavigation() {
        bindIosNotificationNavigation(MapmoryNavigation())
        bindIosNotificationNavigation(null)
    }

    @Test
    fun registrationMetadataSurvivesRecreationAndClear() {
        val namespace = "push-store-test-${platform.Foundation.NSUUID().UUIDString}"
        val store = com.mapmory.shared.data.settings.IosPushTokenRegistrationStore(namespace)
        val registration = com.mapmory.shared.data.settings.PushTokenRegistration("account-42", 7L)
        try {
            store.write(registration)
            val recreated = com.mapmory.shared.data.settings.IosPushTokenRegistrationStore(namespace)
            assertEquals(registration, recreated.read())
            recreated.clear()
            assertEquals(null, store.read())
        } finally {
            store.clear()
        }
    }

    @Test
    fun coldStartTapWaitsForNavigationAndInvalidPayloadDoesNotReplaceIt() {
        bindIosNotificationNavigation(null)
        receiveIosPushNotification("PHOTO_RECORD_COMPLETED", "42", null, true)
        receiveIosPushNotification("VIDEO_COMPLETED", null, "-1", false)
        val navigation = MapmoryNavigation()
        bindIosNotificationNavigation(navigation)
        val request = navigation.notificationRequest.value!!
        assertEquals(42L, assertIs<NotificationNavigationTarget.Record>(request.target).id)
        assertFalse(request.preserveBackStack)
        val replacement = MapmoryNavigation()
        bindIosNotificationNavigation(replacement)
        assertFalse(replacement.hasPendingNotification())
    }

    @Test
    fun warmVideoTapUsesExistingNavigationAndRejectsMalformedTargets() {
        val navigation = MapmoryNavigation()
        bindIosNotificationNavigation(navigation)
        for (id in listOf(null, "", "0", "-1", "9.2", "9223372036854775808")) {
            receiveIosPushNotification("VIDEO_COMPLETED", null, id, true)
            assertFalse(navigation.hasPendingNotification())
        }
        receiveIosPushNotification("unrecognized", "42", "7", true)
        assertFalse(navigation.hasPendingNotification())
        receiveIosPushNotification("VIDEO_COMPLETED", null, "7", true)
        val request = navigation.notificationRequest.value!!
        assertEquals(7L, assertIs<NotificationNavigationTarget.Video>(request.target).jobId)
        assertTrue(request.preserveBackStack)
    }
}
