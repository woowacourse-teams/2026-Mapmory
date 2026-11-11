@file:OptIn(kotlin.experimental.ExperimentalNativeApi::class)

package com.mapmory.shared.data.media

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.Foundation.NSDate
import platform.Foundation.NSLog
import kotlin.native.Platform

/**
 * 업로드한 원본으로 약 1080px 미리보기를 만들어 기기 캐시의 작은 목록 미리보기(256px)를 바꾼다(#686).
 * 서버 썸네일(#631)과 같은 긴 변 1080px이다. 로컬 키와 서버 objectKey에 모두 써서,
 * 저장 뒤 로컬 키에서 objectKey로 복사하는 단계가 작은 미리보기를 되살리지 않게 한다.
 */
class IosSavedPhotoPreviewWriter(
    private val cache: PhotoPreviewCache = IosPhotoPreviewCache(),
) : SavedPhotoPreviewWriter {
    override suspend fun write(localId: String, objectKey: String, originalBytes: ByteArray): Boolean =
        replace(localId, objectKey, source = "upload") {
            originalBytes.iosPhotoData().photoDisplayJpeg(SavedPreviewSizePx)
        }

    /** Decodes the staged upload file straight from disk, without loading the original into memory (#693). */
    override suspend fun writeFromFile(localId: String, objectKey: String, path: String): Boolean =
        replace(localId, objectKey, source = "staged_file") {
            iosPhotoFileDisplayJpeg(path, SavedPreviewSizePx)
        }

    private suspend fun replace(
        localId: String,
        objectKey: String,
        source: String,
        makePreview: () -> ByteArray?,
    ): Boolean {
        val startedAt = NSDate().timeIntervalSinceReferenceDate
        // Decoding, encoding and the file writes run off the caller's thread, one photo at a time.
        val written = withContext(Dispatchers.Default) {
            val preview = makePreview() ?: return@withContext null
            // IosPhotoPreviewCache.write fails silently, so both keys are read back. The save later copies
            // the local key onto the objectKey; reporting a replacement while the local key still holds the
            // small preview would let that copy bring it back. Otherwise the caller keeps its own preview.
            val landed = listOf(objectKey, localId).all { key ->
                cache.write(key, preview)
                cache.read(key)?.size == preview.size
            }
            if (landed) preview.size else null
        } ?: return false
        if (Platform.isDebugBinary) {
            val elapsed = ((NSDate().timeIntervalSinceReferenceDate - startedAt) * 1000).toLong()
            NSLog("MapmoryPhotoPerf saved_preview_ms=$elapsed bytes=$written source=$source")
        }
        return true
    }
}

private const val SavedPreviewSizePx = 1080
