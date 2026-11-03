package com.mapmory.shared.presentation.auth

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import com.mapmory.shared.analytics.LocalMapmoryAnalytics
import com.mapmory.shared.analytics.MapmoryAnalyticsEvent
import com.mapmory.shared.analytics.RecordingAnalytics
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class)
class SocialLoginPanelAnalyticsTest {
    @Test fun `panel click emits start and late callback after disposal still emits one result`() {
        val logger = RecordingAnalytics()
        var callback: ((SocialLoginResult) -> Unit)? = null
        val launcher = object : SocialLoginLauncher {
            override val providers = listOf(SocialProvider.GOOGLE)
            override fun launch(provider: SocialProvider, onResult: (SocialLoginResult) -> Unit) { callback = onResult }
        }
        val visible = mutableStateOf(true)
        val scene = ImageComposeScene(412, 892) {
            CompositionLocalProvider(LocalMapmoryAnalytics provides logger, LocalSocialLoginLauncher provides launcher) {
                if (visible.value) SocialLoginPanel()
            }
        }
        var frame = 0L
        fun render() { repeat(30) { scene.render(++frame * 16_666_667L).close() } }
        try {
            render(); render()
            assertEquals(1, logger.events.count { it.first == MapmoryAnalyticsEvent.SOCIAL_LOGIN_VIEWED })
            // Locate the only interactive provider row without coupling the test to text wrapping.
            for (y in 100..600 step 20) {
                scene.sendPointerEvent(PointerEventType.Press, Offset(200f, y.toFloat()), type = PointerType.Touch)
                scene.sendPointerEvent(PointerEventType.Release, Offset(200f, y.toFloat()), type = PointerType.Touch)
                render()
                if (callback != null) break
            }
            assertNotNull(callback, "First provider button must launch authentication")
            assertEquals(1, logger.events.count { it.first == MapmoryAnalyticsEvent.SOCIAL_LOGIN_STARTED })
            visible.value = false
            render()
            callback!!(SocialLoginCancelled)
            callback!!(SocialLoginCancelled)
            assertEquals(1, logger.events.count { it.first == MapmoryAnalyticsEvent.SOCIAL_LOGIN_COMPLETED })
            assertTrue(logger.events.none { it.first == MapmoryAnalyticsEvent.APP_ERROR })
        } finally { scene.close() }
    }
}
