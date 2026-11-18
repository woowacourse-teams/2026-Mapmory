package com.mapmory.android

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotificationReproductionPolicyTest {
    private val developmentApi = "https://dev-api.map-mory.com/api/v1"

    @Test fun notificationReproductionIsRestrictedToTheExactDevelopmentApi() {
        assertTrue(NotificationReproductionPolicy.enabled(developmentApi))
        assertTrue(NotificationReproductionPolicy.enabled("$developmentApi/"))
        listOf(
            "https://api.map-mory.com/api/v1",
            "http://dev-api.map-mory.com/api/v1",
            "https://dev-api.map-mory.com:8443/api/v1",
            "https://dev-api.map-mory.com.evil/api/v1",
            "$developmentApi?environment=production",
        ).forEach { base ->
            assertFalse(NotificationReproductionPolicy.enabled(base))
            assertNull(NotificationReproductionPolicy.recordId(base, "1"))
        }
    }

    @Test fun destinationRequiresAnExplicitPositiveRecordIdWithoutOverflow() {
        assertEquals(1L, NotificationReproductionPolicy.recordId(developmentApi, "1"))
        assertEquals(Long.MAX_VALUE, NotificationReproductionPolicy.recordId(developmentApi, Long.MAX_VALUE.toString()))
        listOf("", "0", "-1", "+1", "1.0", "1e3", "record/1", "9223372036854775808").forEach {
            assertNull(NotificationReproductionPolicy.recordId(developmentApi, it))
        }
    }
}
