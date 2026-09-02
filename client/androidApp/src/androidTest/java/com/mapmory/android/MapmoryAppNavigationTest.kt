package com.mapmory.android

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import com.mapmory.shared.MapmoryApp
import com.mapmory.shared.app.createInMemoryAppContainer
import com.mapmory.shared.presentation.photo.SelectedPhoto
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test

class MapmoryAppNavigationTest {
    @get:Rule
    val composeRule = createComposeRule()

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

        composeRule.onNodeWithText("일지").performClick()
        composeRule.onNodeWithContentDescription("계측 테스트 여행").assertIsDisplayed()
        composeRule.onNodeWithText("계측 테스트 여행").assertIsDisplayed().performClick()
        composeRule.onNodeWithContentDescription("계측 테스트 여행 사진 1").assertIsDisplayed()

        composeRule.onNodeWithContentDescription("더보기").performClick()
        composeRule.onNodeWithText("수정").performClick()
        composeRule.onNodeWithText("기록 수정하기").assertIsDisplayed()
        composeRule.onNodeWithText("계측 테스트 여행")
            .performTextReplacement("수정된 계측 테스트 여행")
        composeRule.onNodeWithText("저장").performClick()

        composeRule.onNodeWithText("수정된 계측 테스트 여행").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("뒤로가기").performClick()
        composeRule.onNodeWithContentDescription("수정된 계측 테스트 여행")
            .assertIsDisplayed()
            .performClick()

        composeRule.onNodeWithContentDescription("더보기").performClick()
        composeRule.onNodeWithText("삭제").performClick()
        composeRule.onNodeWithText("여행 기록 삭제").assertIsDisplayed()
        composeRule.onNodeWithText("삭제").performClick()

        composeRule.onNodeWithText("아직 작성한 여행 기록이 없어요.").assertIsDisplayed()

        composeRule.onNodeWithContentDescription("새 기록 작성").assertIsDisplayed().performClick()
        composeRule.onNodeWithText("기록 남기기").assertIsDisplayed()
        composeRule.onNodeWithText("←").performClick()
        composeRule.onNodeWithText("아직 작성한 여행 기록이 없어요.").assertIsDisplayed()

        composeRule.onNodeWithText("지도").performClick()
        composeRule.onNodeWithContentDescription("새 기록 작성").performClick()
        composeRule.onNodeWithText("기록 남기기").assertIsDisplayed()
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
}
