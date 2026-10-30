package com.mapmory.shared.data.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class AndroidPhotoPreviewCache(context: Context) : PhotoPreviewCache {
    private val usagePreferences = context.applicationContext.getSharedPreferences("recorded-photos", Context.MODE_PRIVATE)
    override suspend fun readRecordedPhotoIndex(): String? = withContext(Dispatchers.IO) {
        usagePreferences.getString("records", null)
    }
    override suspend fun writeRecordedPhotoIndex(value: String) {
        withContext(Dispatchers.IO) {
            check(usagePreferences.edit().putString("records", value).commit())
        }
    }
    private val metadataPreferences = context.applicationContext.getSharedPreferences("record-photo-dates", Context.MODE_PRIVATE)
    override suspend fun capturedAt(objectKey: String): String? = withContext(Dispatchers.IO) {
        metadataPreferences.getString(objectKey, null)
    }
    override suspend fun rememberCapturedAt(objectKey: String, capturedAt: String) {
        withContext(Dispatchers.IO) {
            check(metadataPreferences.edit().putString(objectKey, capturedAt).commit())
        }
    }
    private val directory = File(context.applicationContext.cacheDir, CacheDirectoryName)
    private val mutex = Mutex()

    override suspend fun read(objectKey: String): ByteArray? = withContext(Dispatchers.IO) {
        mutex.withLock {
            val file = File(directory, objectKeyCacheFileName(objectKey))
            file.takeIf(File::isFile)?.readBytes()?.takeIf(ByteArray::isNotEmpty)
        }
    }

    override suspend fun write(objectKey: String, bytes: ByteArray) {
        if (objectKey.isBlank() || bytes.isEmpty() || bytes.size > MaxPreviewCacheEntryBytes) return
        withContext(Dispatchers.IO) {
            mutex.withLock {
                if (!directory.exists() && !directory.mkdirs()) return@withLock
                val destination = File(directory, objectKeyCacheFileName(objectKey))
                val temporary = File(directory, "${destination.name}.tmp")
                temporary.writeBytes(bytes)
                if (!temporary.renameTo(destination)) {
                    destination.writeBytes(bytes)
                    temporary.delete()
                }
            }
        }
    }

    override suspend fun uri(objectKey: String): String? = withContext(Dispatchers.IO) {
        mutex.withLock {
            File(directory, objectKeyCacheFileName(objectKey))
                .takeIf(File::isFile)
                ?.toURI()
                ?.toString()
        }
    }

    override suspend fun copy(fromObjectKey: String, toObjectKey: String) {
        if (fromObjectKey == toObjectKey || toObjectKey.isBlank()) return
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val source = File(directory, objectKeyCacheFileName(fromObjectKey))
                if (!source.isFile) return@withLock
                if (!directory.exists() && !directory.mkdirs()) return@withLock
                source.copyTo(File(directory, objectKeyCacheFileName(toObjectKey)), overwrite = true)
            }
        }
    }

    override suspend fun linkLocalSource(objectKey: String, localSourceKey: String) {
        if (objectKey.isBlank() || localSourceKey.isBlank()) return
        withContext(Dispatchers.IO) {
            mutex.withLock {
                if (!directory.exists() && !directory.mkdirs()) return@withLock
                File(directory, "${objectKeyCacheFileName(objectKey)}$LocalSourceSuffix")
                    .writeText(localSourceKey)
            }
        }
    }

    override suspend fun localSourceKey(objectKey: String): String? = withContext(Dispatchers.IO) {
        mutex.withLock {
            File(directory, "${objectKeyCacheFileName(objectKey)}$LocalSourceSuffix")
                .takeIf(File::isFile)
                ?.takeIf { it.length() in 1..MaxLocalSourceKeyBytes }
                ?.readText()
                ?.takeIf(String::isNotBlank)
        }
    }
}

internal actual fun createRemotePhotoPreview(originalBytes: ByteArray): ByteArray? = try {
    createAndroidPhotoPreview(originalBytes)
} catch (_: RuntimeException) {
    // Android 로컬 JVM 테스트의 framework stub과 손상된 이미지 디코더 오류를 허용한다.
    null
}

private fun createAndroidPhotoPreview(originalBytes: ByteArray): ByteArray? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(originalBytes, 0, originalBytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    var sampleSize = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / sampleSize > PreviewSizePx * 2) {
        sampleSize *= 2
    }
    val decoded = BitmapFactory.decodeByteArray(
        originalBytes,
        0,
        originalBytes.size,
        BitmapFactory.Options().apply { inSampleSize = sampleSize },
    ) ?: return null
    val oriented = originalBytes.exifOrientationMatrix()?.let { matrix ->
        Bitmap.createBitmap(
            decoded,
            0,
            0,
            decoded.width,
            decoded.height,
            matrix,
            true,
        )
    } ?: decoded
    val maxDimension = maxOf(oriented.width, oriented.height)
    val preview = if (maxDimension > PreviewSizePx) {
        val scale = PreviewSizePx.toFloat() / maxDimension
        Bitmap.createScaledBitmap(
            oriented,
            (oriented.width * scale).toInt().coerceAtLeast(1),
            (oriented.height * scale).toInt().coerceAtLeast(1),
            true,
        )
    } else {
        oriented
    }

    return try {
        ByteArrayOutputStream().use { output ->
            if (!preview.compress(Bitmap.CompressFormat.JPEG, PreviewJpegQuality, output)) null
            else output.toByteArray()
        }
    } finally {
        if (preview !== oriented) preview.recycle()
        if (oriented !== decoded) oriented.recycle()
        decoded.recycle()
    }
}

private fun ByteArray.exifOrientationMatrix(): Matrix? {
    val orientation = ExifInterface(ByteArrayInputStream(this)).getAttributeInt(
        ExifInterface.TAG_ORIENTATION,
        ExifInterface.ORIENTATION_NORMAL,
    )
    return Matrix().apply {
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> setScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> setRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> {
                setRotate(180f)
                postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                setRotate(90f)
                postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_90 -> setRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                setRotate(-90f)
                postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_270 -> setRotate(-90f)
            else -> return null
        }
    }
}

private const val CacheDirectoryName = "mapmory-photo-previews"
private const val MaxPreviewCacheEntryBytes = 5 * 1024 * 1024
private const val PreviewSizePx = 1280
private const val PreviewJpegQuality = 85
private const val LocalSourceSuffix = ".source"
private const val MaxLocalSourceKeyBytes = 4_096L
