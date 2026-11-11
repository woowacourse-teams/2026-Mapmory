package com.mapmory.shared

import com.mapmory.shared.navigation.RecordsRoute
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MapmoryNavigationTest {
    @Test
    fun `push data accepts only supported events and positive identifiers`() {
        assertEquals(
            MapmoryNotificationTarget.Record(42),
            parseMapmoryNotificationData(mapOf("type" to "PHOTO_RECORD_COMPLETED", "recordId" to "42")),
        )
        assertEquals(
            MapmoryNotificationTarget.Video(91),
            parseMapmoryNotificationData(mapOf("type" to "VIDEO_COMPLETED", "jobId" to "91")),
        )
        assertNull(parseMapmoryNotificationData(mapOf("type" to "VIDEO_COMPLETED", "jobId" to "0")))
        assertNull(parseMapmoryNotificationData(mapOf("type" to "PHOTO_RECORD_COMPLETED", "recordId" to "bad")))
        assertNull(parseMapmoryNotificationData(mapOf("type" to "UNKNOWN", "recordId" to "42")))
    }

    @Test
    fun `invalid notification identifiers are ignored`() {
        val navigation = MapmoryNavigation()

        navigation.openRecordNotification(0, preserveBackStack = false)
        navigation.openVideoNotification(-1, preserveBackStack = true)

        assertNull(navigation.notificationRequest.value)
    }

    @Test
    fun `record notification request preserves target and cold launch policy`() {
        val navigation = MapmoryNavigation()

        navigation.openRecordNotification(42, preserveBackStack = false)

        val request = requireNotNull(navigation.notificationRequest.value)
        assertEquals(NotificationNavigationTarget.Record(42), request.target)
        assertFalse(request.preserveBackStack)
    }

    @Test
    fun `video request can await login and resumes to the previous route`() {
        val navigation = MapmoryNavigation()
        val returnRoute = RecordsRoute(locationId = 7)

        navigation.openVideoNotification(91, preserveBackStack = true)
        val requestId = requireNotNull(navigation.notificationRequest.value).id
        navigation.awaitNotificationLogin(requestId, returnRoute)

        val waitingRequest = requireNotNull(navigation.notificationRequest.value)
        assertEquals(NotificationNavigationTarget.Video(91), waitingRequest.target)
        assertTrue(waitingRequest.preserveBackStack)
        assertTrue(waitingRequest.awaitingLogin)
        assertEquals(returnRoute, waitingRequest.returnRoute)

        navigation.consumeNotification(requestId + 1)
        assertEquals(waitingRequest, navigation.notificationRequest.value)
        navigation.consumeNotification(requestId)
        assertNull(navigation.notificationRequest.value)
    }

    @Test
    fun `leaving login can cancel only the matching notification request`() {
        val navigation = MapmoryNavigation()

        navigation.openRecordNotification(42, preserveBackStack = true)
        val requestId = requireNotNull(navigation.notificationRequest.value).id
        navigation.awaitNotificationLogin(requestId, RecordsRoute())

        navigation.cancelNotification(requestId + 1)
        assertTrue(navigation.hasPendingNotification())

        navigation.cancelNotification(requestId)
        assertFalse(navigation.hasPendingNotification())
        assertNull(navigation.notificationRequest.value)
    }
}
