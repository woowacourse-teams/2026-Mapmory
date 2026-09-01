package com.mapmory.shared.presentation.photo

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import androidx.test.platform.app.InstrumentationRegistry
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PhotoLibraryAndroidDeviceTest {
    @Test
    fun `MediaStore_스냅샷과_사진_리더는_Android_ContentResolver를_사용한다`() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val fixture = insertFixturePhoto(context)
        try {
            val candidate = context.queryPhotoMetadataSnapshot()
                ?.single { photo -> photo.contentUri == fixture.uri.toString() }

            assertNotNull(candidate)
            assertEquals("mapmory-test.jpg", candidate.displayName)
            assertEquals("image/jpeg", candidate.mimeType)

            val coordinates = withMediaLocationPermission {
                context.readCoordinates(fixture.uri)
            }
            assertNotNull(coordinates)
            assertEquals(37.4979, coordinates.first, 0.0001)
            assertEquals(127.0276, coordinates.second, 0.0001)

            val selectedPhoto = context.readPhoto(fixture.uri)
            assertNotNull(selectedPhoto)
            assertEquals("mapmory-test.jpg", selectedPhoto.displayName)
            assertTrue(selectedPhoto.originalBytes?.isNotEmpty() == true)
            assertTrue(selectedPhoto.previewBytes?.isNotEmpty() == true)

            val previewOnlyPhoto = context.readPhoto(
                uri = fixture.uri,
                includeOriginalBytes = false,
            )
            assertNotNull(previewOnlyPhoto)
            assertNull(previewOnlyPhoto.originalBytes)
            assertTrue(previewOnlyPhoto.previewBytes?.isNotEmpty() == true)
        } finally {
            context.contentResolver.delete(fixture.uri, null, null)
            fixture.file.delete()
        }
    }

    private fun insertFixturePhoto(context: Context): FixturePhoto {
        val file = File.createTempFile("mapmory-photo-", ".jpg", context.cacheDir)
        val bitmap = Bitmap.createBitmap(64, 32, Bitmap.Config.ARGB_8888)
        try {
            ByteArrayOutputStream().use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.JPEG, 100, output))
                file.writeBytes(output.toByteArray())
            }
        } finally {
            bitmap.recycle()
        }
        ExifInterface(file).apply {
            setLatLong(37.4979, 127.0276)
            saveAttributes()
        }

        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "mapmory-test.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.DATE_TAKEN, 1_700_000_000_000L)
            put(MediaStore.Images.Media.DATE_MODIFIED, 1_700_000_000L)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/MapmoryTest")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        val uri = requireNotNull(
            context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values),
        )
        context.contentResolver.openOutputStream(uri).use { output ->
            requireNotNull(output).write(file.readBytes())
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            context.contentResolver.update(
                uri,
                ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) },
                null,
                null,
            )
        }
        return FixturePhoto(uri, file)
    }

    private fun <T> withMediaLocationPermission(block: () -> T): T {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        automation.adoptShellPermissionIdentity(Manifest.permission.ACCESS_MEDIA_LOCATION)
        return try {
            block()
        } finally {
            automation.dropShellPermissionIdentity()
        }
    }

    private data class FixturePhoto(
        val uri: Uri,
        val file: File,
    )
}
