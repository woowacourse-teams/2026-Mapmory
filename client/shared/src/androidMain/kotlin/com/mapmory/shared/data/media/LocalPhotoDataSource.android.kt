package com.mapmory.shared.data.media

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import com.mapmory.shared.data.local.photo.PhotoMetadataDatabase
import kotlinx.coroutines.CancellationException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AndroidLocalPhotoDataSource(
    context: Context,
) : LocalPhotoDataSource {
    private val applicationContext = context.applicationContext

    override suspend fun capturedAt(localId: String): String? = withContext(Dispatchers.IO) {
        try {
            val indexed = PhotoMetadataDatabase.getInstance(applicationContext).photoMetadataDao()
                .capturedAtMillis(localId)?.takeIf { it > 0 }
            val uri = Uri.parse(localId)
            val taken = indexed ?: runCatching {
                applicationContext.contentResolver.query(
                    uri, arrayOf(MediaStore.Images.Media.DATE_TAKEN), null, null, null,
                )?.use { cursor ->
                    if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0).takeIf { it > 0 } else null
                }
            }.getOrNull()
            if (taken != null) {
                SimpleDateFormat("yyyy.MM.dd", Locale.KOREA).format(Date(taken))
            } else {
                applicationContext.contentResolver.openInputStream(uri)?.use { input ->
                    ExifInterface(input).getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
                        ?.take(10)?.replace(':', '-')
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun read(localId: String): ByteArray? = withContext(Dispatchers.IO) {
        runCatching {
            applicationContext.contentResolver
                .openInputStream(Uri.parse(localId))
                ?.use { input -> input.readBytes() }
                ?.takeIf(ByteArray::isNotEmpty)
        }.getOrNull()
    }
}
