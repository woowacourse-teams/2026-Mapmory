package com.mapmory.shared.presentation.map

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CurrentLocationTest {
    @Test
    fun recentLocationCanBeReusedForOneMinute() {
        assertTrue(isRecentCurrentLocation(100_000_000_000L, 100_000_000_000L))
        assertTrue(isRecentCurrentLocation(100_000_000_000L, 40_000_000_000L))
    }

    @Test
    fun staleFutureAndMissingTimestampsAreRejected() {
        assertFalse(isRecentCurrentLocation(100_000_000_000L, 39_999_999_999L))
        assertFalse(isRecentCurrentLocation(100_000_000_000L, 100_000_000_001L))
        assertFalse(isRecentCurrentLocation(1L, 0L))
    }

    @Test
    fun mapCanReuseLocationForTwoMinutesWithoutChangingDefault() {
        val nowNanos = 200_000_000_000L
        val mapCacheMaxAgeNanos = MapCurrentLocationCacheMaxAgeMillis * 1_000_000L

        assertFalse(isRecentCurrentLocation(nowNanos, nowNanos - 90_000_000_000L))
        assertTrue(isRecentCurrentLocation(nowNanos, nowNanos - 90_000_000_000L, mapCacheMaxAgeNanos))
        assertTrue(isRecentCurrentLocation(nowNanos, nowNanos - mapCacheMaxAgeNanos, mapCacheMaxAgeNanos))
        assertFalse(isRecentCurrentLocation(nowNanos, nowNanos - mapCacheMaxAgeNanos - 1L, mapCacheMaxAgeNanos))
    }
}
