package com.mapmory.shared.data.media

import android.content.Context
import android.graphics.Bitmap
import androidx.exifinterface.media.ExifInterface
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class PhotoCapturedAtDeviceTest {
    @Test
    fun `원본_EXIF_촬영일을_읽고_캐시를_다시_만들어도_날짜를_보존한다`() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File.createTempFile("photo-date-", ".jpg", context.cacheDir)
        val key = "test-date-${UUID.randomUUID()}"
        val bitmap = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888)
        try {
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            ExifInterface(file).apply {
                setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, "2026:09:11 12:34:56")
                saveAttributes()
            }
            val date = AndroidLocalPhotoDataSource(context).capturedAt(file.toURI().toString())
            assertEquals("2026-09-11", date)
            AndroidPhotoPreviewCache(context).rememberCapturedAt(key, requireNotNull(date))
            assertEquals(date, AndroidPhotoPreviewCache(context).capturedAt(key))
        } finally {
            bitmap.recycle()
            file.delete()
            context.getSharedPreferences("record-photo-dates", Context.MODE_PRIVATE).edit().remove(key).commit()
        }
    }
}
