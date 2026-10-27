package com.mapmory.shared.presentation.auth

import com.mapmory.shared.analytics.MapmoryAnalytics
import com.mapmory.shared.analytics.MapmoryAnalyticsEvent
import com.mapmory.shared.analytics.RecordingAnalytics
import kotlinx.coroutines.CancellationException
import kotlin.test.*

class SocialLoginAnalyticsTest {
    private val analytics = RecordingAnalytics()
    private val native = FakeLauncher()
    private val launcher = AnalyticsSocialLoginLauncher(native, analytics)
    private fun events(name: String) = analytics.events.filter { it.first == name }.map { it.second }

    @Test fun `provider success is tracked once and never leaks credentials or becomes account login`() {
        SocialProvider.entries.forEach { provider ->
            var delivered = 0
            launcher.launch(provider) { delivered++ }
            val success = SocialLoginSuccess(SocialCredential(provider, idToken = "PRIVATE_TOKEN", displayName = "PRIVATE_NAME", rawNonce = "PRIVATE_NONCE"))
            native.callback(success)
            native.callback(success)
            assertEquals(1, delivered)
        }
        assertEquals(listOf("google", "kakao", "apple"), events(MapmoryAnalyticsEvent.SOCIAL_LOGIN_STARTED).map { it["provider"] })
        assertEquals(3, events(MapmoryAnalyticsEvent.SOCIAL_LOGIN_COMPLETED).size)
        events(MapmoryAnalyticsEvent.SOCIAL_LOGIN_COMPLETED).forEach {
            assertEquals("success", it["outcome"])
            assertEquals("provider_auth", it["stage"])
            assertEquals("false", it["account_linked"])
            assertTrue(it["duration_ms"]!!.toLong() >= 0)
        }
        assertFalse(analytics.events.toString().contains("PRIVATE"))
        assertTrue(events(MapmoryAnalyticsEvent.APP_ERROR).isEmpty())
        assertFalse(analytics.events.any { it.first == "login" })
    }

    @Test fun `failure callback emits one outcome and one sanitized error`() {
        launcher.launch(SocialProvider.KAKAO) {}
        native.callback(SocialLoginFailure("PRIVATE_SDK_MESSAGE"))
        native.callback(SocialLoginFailure("PRIVATE_AGAIN"))
        assertEquals("failure", events(MapmoryAnalyticsEvent.SOCIAL_LOGIN_COMPLETED).single()["outcome"])
        assertEquals("provider_failure", events(MapmoryAnalyticsEvent.APP_ERROR).single()["error_type"])
        assertFalse(analytics.events.toString().contains("PRIVATE"))
    }

    @Test fun `user cancellation emits cancelled outcome without an app error`() {
        launcher.launch(SocialProvider.APPLE) {}
        native.callback(SocialLoginCancelled)
        assertEquals("cancelled", events(MapmoryAnalyticsEvent.SOCIAL_LOGIN_COMPLETED).single()["outcome"])
        assertTrue(events(MapmoryAnalyticsEvent.APP_ERROR).isEmpty())
    }

    @Test fun `synchronous exception is measured once and original exception preserved`() {
        val error = IllegalStateException("PRIVATE_EXCEPTION")
        native.error = error
        assertSame(error, assertFailsWith<IllegalStateException> { launcher.launch(SocialProvider.GOOGLE) {} })
        assertEquals("failure", events(MapmoryAnalyticsEvent.SOCIAL_LOGIN_COMPLETED).single()["outcome"])
        assertEquals("IllegalStateException", events(MapmoryAnalyticsEvent.APP_ERROR).single()["error_type"])
        assertFalse(analytics.events.toString().contains("PRIVATE"))
    }

    @Test fun `coroutine cancellation is preserved and not reported as a failure`() {
        native.error = CancellationException("PRIVATE")
        assertFailsWith<CancellationException> { launcher.launch(SocialProvider.GOOGLE) {} }
        assertEquals("cancelled", events(MapmoryAnalyticsEvent.SOCIAL_LOGIN_COMPLETED).single()["outcome"])
        assertTrue(events(MapmoryAnalyticsEvent.APP_ERROR).isEmpty())
    }

    @Test fun `separate retries produce separate attempts and the original result is delivered`() {
        val failure = SocialLoginFailure("message")
        var received: SocialLoginResult? = null
        repeat(2) {
            launcher.launch(SocialProvider.GOOGLE) { received = it }
            native.callback(failure)
            assertSame(failure, received)
        }
        assertEquals(2, events(MapmoryAnalyticsEvent.SOCIAL_LOGIN_STARTED).size)
        assertEquals(2, events(MapmoryAnalyticsEvent.SOCIAL_LOGIN_COMPLETED).size)
    }

    @Test fun `throwing telemetry does not prevent authentication or result delivery`() {
        val broken = object : MapmoryAnalytics {
            override fun logEvent(name: String, parameters: Map<String, String>) { error("logger failed") }
            override fun setUserProperty(name: String, value: String?) = Unit
        }
        val measured = AnalyticsSocialLoginLauncher(native, broken)
        var result: SocialLoginResult? = null
        measured.panelViewed()
        measured.launch(SocialProvider.APPLE) { result = it }
        native.callback(SocialLoginCancelled)
        assertSame(SocialLoginCancelled, result)
    }

    @Test fun `panel view has no sensitive data and retains provider availability`() {
        launcher.panelViewed()
        assertEquals(native.providers, launcher.providers)
        assertEquals("settings", events(MapmoryAnalyticsEvent.SOCIAL_LOGIN_VIEWED).single()["source"])
    }

    @Test fun `result handler exceptions are not counted as authentication failures`() {
        native.synchronousResult = SocialLoginCancelled
        assertFailsWith<IllegalStateException> { launcher.launch(SocialProvider.APPLE) { error("UI handler") } }
        assertEquals("cancelled", events(MapmoryAnalyticsEvent.SOCIAL_LOGIN_COMPLETED).single()["outcome"])
        assertTrue(events(MapmoryAnalyticsEvent.APP_ERROR).isEmpty())
    }

    private class FakeLauncher : SocialLoginLauncher {
        override val providers = SocialProvider.entries.toList()
        lateinit var callback: (SocialLoginResult) -> Unit
        var error: Exception? = null
        var synchronousResult: SocialLoginResult? = null
        override fun launch(provider: SocialProvider, onResult: (SocialLoginResult) -> Unit) {
            callback = onResult
            error?.let { throw it }
            synchronousResult?.let(onResult)
        }
    }
}
