package com.mapmory.android

import android.Manifest
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Before
import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.mapmory.shared.MapmoryApp
import com.mapmory.shared.app.AppContainer
import com.mapmory.shared.app.MapmoryViewModelFactory
import com.mapmory.shared.app.createInMemoryAppContainer
import com.mapmory.shared.developer.MapmoryDeveloperToolsInfo
import org.junit.Assert.assertEquals
import com.mapmory.shared.presentation.photo.SelectedPhoto
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test

class MapmoryAppNavigationTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun preparePermissionsForEmptyTestEmulator() {
        // Opt in only for the temporary emulator fixture. Never grant permissions on a real phone.
        if (Build.HARDWARE !in setOf("ranchu", "goldfish")) return
        if (InstrumentationRegistry.getArguments().getString("grantFixturePhotoPermissions") != "true") return
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val galleryPermission = if (Build.VERSION.SDK_INT >= 33) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else Manifest.permission.READ_EXTERNAL_STORAGE
        val permissions = buildList {
            add(galleryPermission)
            if (Build.VERSION.SDK_INT >= 29) add(Manifest.permission.ACCESS_MEDIA_LOCATION)
        }
        permissions.forEach { permission ->
            val result = instrumentation.uiAutomation.executeShellCommand(
                "pm grant ${instrumentation.targetContext.packageName} $permission",
            )
            ParcelFileDescriptor.AutoCloseInputStream(result).use { it.readBytes() }
        }
    }

    @Test
    fun `개발자_도구는_디버그_정보가_제공될_때만_설정에서_열린다`() {
        val container = createInMemoryAppContainer()
        composeRule.setContent {
            MapmoryApp(
                container = container,
                developerToolsInfo = MapmoryDeveloperToolsInfo(
                    appVersion = "0.1.7",
                    applicationId = "com.mapmory.android",
                    buildType = "debug",
                    apiBaseUrl = "https://dev-api.map-mory.com/api/v1",
                    deviceName = "Test device",
                    androidVersion = "16 (API 36)",
                ),
            )
        }

        openSettings()
        composeRule.onNodeWithText("개발자 도구").performClick()
        composeRule.onNodeWithText("0.1.7").assertIsDisplayed()
        composeRule.onNodeWithText("com.mapmory.android").assertIsDisplayed()
        composeRule.onNodeWithText("개발 서버").assertIsDisplayed()
        composeRule.onNodeWithText("https://dev-api.map-mory.com/api/v1").assertIsDisplayed()
        composeRule.onNodeWithText("Test device").assertIsDisplayed()
    }

    @Test
    fun `개발자_도구_진입은_정보가_없으면_설정에_노출되지_않는다`() {
        val container = createInMemoryAppContainer()
        composeRule.setContent { MapmoryApp(container = container) }

        openSettings()

        assertEquals(
            0,
            composeRule.onAllNodesWithText("개발자 도구").fetchSemanticsNodes().size,
        )
    }

    @Test
    fun `기록_목록_상세_삭제와_편집_화면은_목적지_ViewModel을_사용한다`() {
        val container = createInMemoryAppContainer()
        val gangnam = container.regionCatalog.requireByCode("11680")
        val photoBytes = createPngBytes()
        runBlocking {
            val editor = container.viewModelFactory.createTripRecordEditorViewModel()
            editor.selectLocation(gangnam)
            editor.updateTitle("계측 테스트 여행")
            editor.updateContent("화면별 ViewModel 연결 확인")
            editor.updateStartDate("2026-08-24")
            editor.addPhotos(
                listOf(
                    SelectedPhoto(
                        id = "local/instrumentation-photo.png",
                        displayName = "instrumentation-photo.png",
                        previewBytes = photoBytes,
                        originalBytes = photoBytes,
                    ),
                ),
            )
            check(editor.save())
        }

        composeRule.setContent {
            MapmoryApp(container = container)
        }

        composeRule.waitUntil(15_000) {
            composeRule.onAllNodesWithText("일지").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("일지").performClick()
        composeRule.onNodeWithContentDescription("강남구").assertIsDisplayed()
        composeRule.onNodeWithText("강남구").assertIsDisplayed().performClick()
        composeRule.onNodeWithText("강남구 사진첩").assertIsDisplayed()
        composeRule.mainClock.autoAdvance = false
        composeRule.onNodeWithContentDescription("2026. 08. 24 여행 사진 확대")
            .assertIsDisplayed()
            .performClick()
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.mainClock.advanceTimeBy(100)
        // The source and destination coexist while the image's bounds are moving.
        composeRule.onNodeWithContentDescription("2026. 08. 24 여행 사진 확대").assertExists()
        composeRule.onNodeWithContentDescription("강남구 확대 사진 1").assertExists()
        composeRule.mainClock.advanceTimeBy(400)
        composeRule.onNodeWithContentDescription("2026. 08. 24 여행 사진 확대").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("강남구 확대 사진 1").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("사진첩으로 돌아가기").performClick()
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.mainClock.advanceTimeBy(100)
        composeRule.onNodeWithContentDescription("강남구 확대 사진 1").assertExists()
        composeRule.onNodeWithContentDescription("2026. 08. 24 여행 사진 확대").assertExists()
        composeRule.mainClock.advanceTimeBy(400)
        composeRule.onNodeWithContentDescription("강남구 확대 사진 1").assertDoesNotExist()
        composeRule.mainClock.autoAdvance = true

        composeRule.onNodeWithContentDescription("더보기").performClick()
        composeRule.onNodeWithText("수정").performClick()
        composeRule.waitUntil(15_000) {
            composeRule.onAllNodesWithText("사진 고르기").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("사진 고르기").assertIsDisplayed()
        composeRule.onNode(hasContentDescription("크게 보기", substring = true), useUnmergedTree = true).performScrollTo().performClick()
        composeRule.mainClock.advanceTimeBy(400)
        composeRule.onNodeWithContentDescription("사진 고르기로 돌아가기").assertIsDisplayed()
        composeRule.onNode(hasContentDescription("확대 사진", substring = true)).assertIsDisplayed()
        composeRule.onNodeWithText("선택 해제").performClick()
        composeRule.onNodeWithText("앨범에 추가").performClick()
        composeRule.onNodeWithContentDescription("사진 고르기로 돌아가기").performClick()
        composeRule.mainClock.advanceTimeBy(400)
        composeRule.onNodeWithText("사진 고르기").assertIsDisplayed()
        composeRule.onNodeWithText("수정하기").performClick()
        composeRule.waitUntil(15_000) {
            composeRule.onAllNodesWithText("강남구 사진첩").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithText("강남구 사진첩").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("뒤로가기").performClick()
        composeRule.onNodeWithContentDescription("강남구")
            .assertIsDisplayed()
            .performClick()

        composeRule.onNodeWithContentDescription("더보기").performClick()
        composeRule.onNodeWithText("삭제").performClick()
        composeRule.onNodeWithText("여행 기록 삭제").assertIsDisplayed()
        composeRule.onNodeWithText("삭제").performClick()

        composeRule.onNodeWithText("아직 작성한 여행 기록이 없어요.").assertIsDisplayed()

        composeRule.onNodeWithContentDescription("새 기록 작성").assertIsDisplayed().performClick()
        composeRule.onNodeWithText("어디 사진을 불러올까요?").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("뒤로가기").performClick()
        composeRule.onNodeWithText("아직 작성한 여행 기록이 없어요.").assertIsDisplayed()

        composeRule.onNodeWithText("지도").performClick()
        composeRule.onNodeWithContentDescription("새 기록 작성").performClick()
        composeRule.onNodeWithText("어디 사진을 불러올까요?").assertIsDisplayed()
    }

    @Test
    fun `탭을_반복_이동해도_목록과_통계_ViewModel을_다시_만들지_않는다`() {
        val original = createInMemoryAppContainer()
        var listCreations = 0
        var statisticsCreations = 0
        val factory = object : MapmoryViewModelFactory by original.viewModelFactory {
            override fun createTripRecordListViewModel() =
                original.viewModelFactory.createTripRecordListViewModel().also { listCreations++ }
            override fun createTripStatisticsViewModel() =
                original.viewModelFactory.createTripStatisticsViewModel().also { statisticsCreations++ }
        }
        val container = object : AppContainer by original {
            override val viewModelFactory = factory
        }
        composeRule.setContent { MapmoryApp(container = container) }
        composeRule.waitUntil(15_000) {
            composeRule.onAllNodesWithText("일지").fetchSemanticsNodes().isNotEmpty()
        }
        repeat(3) {
            composeRule.onNodeWithText("일지").performClick()
            composeRule.onNodeWithText("아직 작성한 여행 기록이 없어요.").assertIsDisplayed()
            composeRule.onNodeWithText("통계").performClick()
            composeRule.waitForIdle()
            composeRule.onNodeWithText("지도").performClick()
        }
        composeRule.runOnIdle {
            assertEquals(1, listCreations)
            assertEquals(1, statisticsCreations)
        }
    }

    private fun createPngBytes(): ByteArray {
        val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.RED)
        return ByteArrayOutputStream().use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
            bitmap.recycle()
            output.toByteArray()
        }
    }

    private fun openSettings() {
        composeRule.waitUntil(15_000) {
            composeRule.onAllNodesWithText("통계").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("통계").performClick()
        composeRule.onNodeWithText("⚙").performClick()
    }
}
