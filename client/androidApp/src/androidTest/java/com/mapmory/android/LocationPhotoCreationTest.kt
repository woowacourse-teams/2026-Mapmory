package com.mapmory.android

import android.Manifest
import android.content.ContentValues
import android.graphics.Bitmap
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.platform.app.InstrumentationRegistry
import com.mapmory.shared.MapmoryApp
import com.mapmory.shared.MapmoryNavigation
import com.mapmory.shared.app.createInMemoryAppContainer
import com.mapmory.shared.domain.model.TripRecordQuery
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class LocationPhotoCreationTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun locationSearchExitAndDirectPhotoSave() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val permission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES
            else Manifest.permission.READ_EXTERNAL_STORAGE
        listOf(permission, Manifest.permission.ACCESS_MEDIA_LOCATION).forEach {
            instrumentation.uiAutomation.executeShellCommand("pm grant ${context.packageName} $it").close()
        }
        val fixtures = mutableListOf<Uri>()
        val container = createInMemoryAppContainer()
        val navigation = MapmoryNavigation()
        try {
            fixtures += insertPhoto("mapmory-japan-14.jpg", "2026-09-14", located = true)
            fixtures += insertPhoto("mapmory-no-gps-15.jpg", "2026-09-15", located = false)
            fixtures += insertPhoto("mapmory-japan-16.jpg", "2026-09-16", located = true)
            fixtures += insertPhoto("Screenshot-mapmory-15.jpg", "2026-09-15", located = false)
            composeRule.setContent { MapmoryApp(container = container, navigation = navigation) }
            composeRule.waitUntil(15_000) {
                composeRule.onAllNodesWithText("일지").fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithText("일지").performClick()
            composeRule.onNodeWithContentDescription("새 기록 작성").performClick()
            composeRule.onNodeWithText("어디 사진을 불러올까요?").assertIsDisplayed()
            capture("location")
            composeRule.onNodeWithText("도시 또는 국가 검색").performTextInput("일본")
            composeRule.onNode(hasText("일본") and !hasSetTextAction()).performClick()
            awaitPicker()
            // Uses the same entry point as the platform system back button.
            composeRule.runOnIdle { assertTrue(navigation.popBackStack()) }
            composeRule.onNodeWithText("작성 중인 기록이 있어요").assertIsDisplayed()
            composeRule.onNodeWithText("계속 작성").performClick()
            composeRule.onNodeWithText("사진 고르기").assertIsDisplayed()
            composeRule.onNodeWithContentDescription("뒤로가기").performClick()
            composeRule.onNodeWithText("작성 중인 기록이 있어요").assertIsDisplayed()
            composeRule.onNodeWithText("나가기").performClick()
            composeRule.onNodeWithContentDescription("새 기록 작성").performClick()
            composeRule.onNodeWithText("도시 또는 국가 검색").performTextInput("일본")
            composeRule.onNode(hasText("일본") and !hasSetTextAction()).performClick()
            awaitPicker()
            composeRule.onNodeWithText("모두 선택").performClick()
            composeRule.onNodeWithText("3장 선택").assertIsDisplayed()
            capture("picker")
            composeRule.onNodeWithText("기록하기").performClick()
            composeRule.waitUntil(15_000) {
                composeRule.onAllNodesWithText("사진으로 남긴 여행").fetchSemanticsNodes().isNotEmpty()
            }
            capture("card")
            runBlocking {
                val saved = container.tripRecordRepository.getTripRecords(TripRecordQuery()).getOrThrow()
                    .records.single()
                val detail = container.tripRecordRepository.getTripRecord(saved.id).getOrThrow()
                assertEquals(3, detail.media.size)
                val dates = detail.media.mapNotNull { it.capturedAt?.replace('.', '-') }.sorted()
                assertEquals(dates.first(), detail.startDate)
                assertEquals(dates.last().takeIf { it != dates.first() }, detail.endDate)
                assertEquals("", detail.title)
                assertEquals("", detail.content)
            }
        } finally {
            fixtures.forEach { context.contentResolver.delete(it, null, null) }
            container.close()
        }
    }

    private fun capture(name: String) {
        composeRule.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bitmap = instrumentation.uiAutomation.takeScreenshot() ?: return
        try {
            File(instrumentation.targetContext.filesDir, "creation-$name.png").outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        } finally {
            bitmap.recycle()
        }
    }

    private fun awaitPicker() {
        try {
            composeRule.waitUntil(30_000) {
                composeRule.onAllNodesWithText("사진 고르기").fetchSemanticsNodes().isNotEmpty()
            }
        } catch (error: Throwable) {
            capture("failure")
            throw error
        }
    }

    private fun insertPhoto(name: String, date: String, located: Boolean): Uri {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File.createTempFile("mapmory-creation", ".jpg", context.cacheDir)
        val bitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
        try {
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }
            ExifInterface(file.absolutePath).apply {
                setAttribute(ExifInterface.TAG_DATETIME, date.replace('-', ':') + " 12:00:00")
                setAttribute("DateTimeOriginal", date.replace('-', ':') + " 12:00:00")
                if (located) {
                    setAttribute(ExifInterface.TAG_GPS_LATITUDE, "35/1,40/1,34/1")
                    setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, "N")
                    setAttribute(ExifInterface.TAG_GPS_LONGITUDE, "139/1,39/1,1/1")
                    setAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF, "E")
                }
                saveAttributes()
            }
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.DATE_TAKEN,
                    LocalDate.parse(date).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli())
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/MapmoryCreationTest")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val uri = requireNotNull(context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values))
            context.contentResolver.openOutputStream(uri).use { requireNotNull(it).write(file.readBytes()) }
            context.contentResolver.update(uri, ContentValues().apply {
                put(MediaStore.Images.Media.IS_PENDING, 0)
            }, null, null)
            return uri
        } finally {
            bitmap.recycle()
            file.delete()
        }
    }
}
