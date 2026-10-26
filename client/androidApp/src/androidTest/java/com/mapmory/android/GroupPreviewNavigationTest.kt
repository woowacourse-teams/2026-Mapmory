package com.mapmory.android

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.mapmory.shared.MapmoryApp
import com.mapmory.shared.app.createInMemoryAppContainer
import com.mapmory.shared.developer.MapmoryDeveloperToolsInfo
import org.junit.Rule
import org.junit.Test

class GroupPreviewNavigationTest {
    @get:Rule val compose = createComposeRule()

    private fun show(developer: Boolean) {
        val container = createInMemoryAppContainer()
        val info = if (developer) MapmoryDeveloperToolsInfo("test", 1, "fixture", "debug", "https://example.com", "fixture", "35") else null
        compose.setContent { MapmoryApp(container = container, developerToolsInfo = info) }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("그룹").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("그룹").performClick()
        compose.waitForIdle()
    }

    @Test fun productionGroupDoesNotExposeSamplePreview() {
        show(developer = false)
        compose.onNodeWithText("그룹 앨범 화면 미리보기").assertDoesNotExist()
        compose.onNodeWithText("제주에서의 우리").assertDoesNotExist()
    }

    @Test fun developerCanOpenAlbumPreviewWithoutReplacingAccountGroupTab() {
        show(developer = true)
        compose.onNodeWithText("그룹 앨범 화면 미리보기").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("그룹 UI 미리보기").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("그룹 UI 미리보기").performClick()
        compose.onNodeWithText("제주에서의 우리").performScrollTo().performClick()
        compose.onNodeWithText("제주에서 함께한 주말").performScrollTo().performClick()
        compose.onNodeWithText("협재해수욕장", substring = true).assertExists()
        compose.onNodeWithText("지도").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("나의 세계 지도").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("그룹").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("그룹 앨범 화면 미리보기").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("그룹 앨범 화면 미리보기").assertExists()
        compose.onNodeWithText("제주에서의 우리").assertDoesNotExist()
    }
}
