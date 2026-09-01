package com.mapmory.shared.data.media

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.exifinterface.media.ExifInterface
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PhotoPreviewAndroidDeviceTest {
    @Test
    fun `서버에서_받은_사진도_EXIF_방향을_적용해_미리보기를_만든다`() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File.createTempFile("mapmory-oriented-", ".jpg", context.cacheDir)
        val source = Bitmap.createBitmap(40, 80, Bitmap.Config.ARGB_8888)
        try {
            file.outputStream().use { output ->
                check(source.compress(Bitmap.CompressFormat.JPEG, 100, output))
            }
            ExifInterface(file).apply {
                setAttribute(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_ROTATE_90.toString(),
                )
                saveAttributes()
            }

            val previewBytes = assertNotNull(createRemotePhotoPreview(file.readBytes()))
            val preview = assertNotNull(
                BitmapFactory.decodeByteArray(previewBytes, 0, previewBytes.size),
            )
            try {
                assertTrue(preview.width > preview.height)
            } finally {
                preview.recycle()
            }
        } finally {
            source.recycle()
            file.delete()
        }
    }
}
