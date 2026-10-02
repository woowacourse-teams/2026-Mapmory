package com.mapmory.shared.data.media

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AndroidLocalPhotoDataSource(
    context: Context,
) : LocalPhotoDataSource {
    private val applicationContext = context.applicationContext

    override suspend fun read(localId: String): ByteArray? = withContext(Dispatchers.IO) {
        runCatching {
            applicationContext.contentResolver
                .openInputStream(Uri.parse(localId))
                ?.use { input -> input.readBytes() }
                ?.takeIf(ByteArray::isNotEmpty)
        }.getOrNull()
    }
}
