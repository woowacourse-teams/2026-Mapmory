package com.mapmory.shared.presentation.auth

import com.mapmory.shared.analytics.MapmoryAnalytics
import com.mapmory.shared.analytics.MapmoryAnalyticsEvent
import com.mapmory.shared.analytics.emit
import com.mapmory.shared.analytics.reportFailure
import kotlinx.coroutines.CancellationException
import kotlin.time.TimeSource

/** Measures provider authentication only. No credentials, messages or backend login success are sent. */
internal class AnalyticsSocialLoginLauncher(
    private val delegate: SocialLoginLauncher,
    private val analytics: MapmoryAnalytics,
    private val source: String = "settings",
) : SocialLoginLauncher {
    override val providers: List<SocialProvider> get() = delegate.providers

    fun panelViewed() {
        analytics.emit(MapmoryAnalyticsEvent.SOCIAL_LOGIN_VIEWED, context())
    }

    override fun launch(provider: SocialProvider, onResult: (SocialLoginResult) -> Unit) {
        val parameters = context() + ("provider" to provider.name.lowercase())
        val started = TimeSource.Monotonic.markNow()
        var completed = false
        analytics.emit(MapmoryAnalyticsEvent.SOCIAL_LOGIN_STARTED, parameters)

        fun complete(outcome: String) {
            analytics.emit(MapmoryAnalyticsEvent.SOCIAL_LOGIN_COMPLETED, parameters + mapOf(
                "outcome" to outcome,
                "duration_ms" to started.elapsedNow().inWholeMilliseconds.toString(),
            ))
        }

        try {
            delegate.launch(provider) { result ->
                if (!completed) {
                    completed = true
                    complete(when (result) {
                        is SocialLoginSuccess -> "success"
                        SocialLoginCancelled -> "cancelled"
                        is SocialLoginFailure -> "failure"
                    })
                    if (result is SocialLoginFailure) {
                        // The native boundary supplies a display message, not a Throwable or stable SDK code.
                        analytics.emit(MapmoryAnalyticsEvent.APP_ERROR, parameters + mapOf(
                            "operation" to "social_provider_auth",
                            "error_category" to "unknown",
                            "error_type" to "provider_failure",
                            "owner_hint" to "undetermined",
                        ))
                    }
                    // Track outcomes even after the panel is disposed; the panel alone owns UI updates.
                    onResult(result)
                }
            }
        } catch (error: CancellationException) {
            if (!completed) { completed = true; complete("cancelled") }
            throw error
        } catch (error: Exception) {
            // Do not misclassify an exception thrown by the caller's result handler as an SDK failure.
            if (!completed) {
                completed = true
                complete("failure")
                analytics.reportFailure("social_provider_auth", error, parameters)
            }
            throw error
        }
    }

    private fun context() = mapOf(
        "source" to source,
        "stage" to "provider_auth",
        "account_linked" to "false",
    )
}
