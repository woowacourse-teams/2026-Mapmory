package com.mapmory.shared.presentation.components

import androidx.compose.runtime.mutableStateMapOf
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Keeps only the opened photo and its two neighbours while the viewer is composed. */
internal class PhotoViewerOriginalCache {
    private val originals = mutableStateMapOf<String, ByteArray?>()

    operator fun get(id: String): ByteArray? = originals[id]
    fun contains(id: String): Boolean = originals.containsKey(id)

    suspend fun prepare(
        photoIds: List<String>,
        currentPage: Int,
        load: suspend (String) -> ByteArray?,
    ) {
        val nearbyIds = listOf(currentPage, currentPage + 1, currentPage - 1)
            .mapNotNull(photoIds::getOrNull)
        originals.keys.toList().filterNot(nearbyIds::contains).forEach(originals::remove)
        // PhotoKit has one active request: finish current first, then prefetch sequentially.
        for (id in nearbyIds) {
            currentCoroutineContext().ensureActive()
            if (!originals.containsKey(id)) {
                val bytes = load(id)
                currentCoroutineContext().ensureActive()
                originals[id] = bytes
            }
        }
    }
}
