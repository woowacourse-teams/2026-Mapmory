package com.mapmory.shared.data.auth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AuthEnvironmentTest {
    @Test fun separatesServersAndKeepsProductionStorage() {
        assertEquals("production", authTokenStorageKey("https://api.map-mory.com/api/v1/"))
        assertNotEquals("production", authTokenStorageKey("https://dev-api.map-mory.com/api/v1"))
        assertEquals(
            authTokenStorageKey("https://dev-api.map-mory.com/api/v1"),
            authTokenStorageKey("https://dev-api.map-mory.com:443/api/v1/"),
        )
        assertNotEquals(
            authTokenStorageKey("https://dev-api.map-mory.com/api/v1"),
            authTokenStorageKey("https://dev-api.map-mory.com:8443/api/v1"),
        )
        assertTrue(isDevelopmentAuthEnvironment("https://dev-api.map-mory.com/api/v1"))
        assertFalse(isDevelopmentAuthEnvironment("https://api.map-mory.com/api/v1"))
    }
}
