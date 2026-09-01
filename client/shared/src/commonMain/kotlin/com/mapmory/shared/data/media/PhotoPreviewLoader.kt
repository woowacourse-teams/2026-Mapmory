package com.mapmory.shared.data.media

import com.mapmory.shared.data.remote.MapmoryApiException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal fun interface PhotoRemoteSource {
    suspend fun download(url: String): Result<ByteArray>
}

internal class PhotoPreviewLoader(
    private val cache: PhotoPreviewCache,
    private val remoteSource: PhotoRemoteSource,
) {
    suspend fun load(
        objectKey: String,
        presignedGetUrl: String,
    ): Result<ByteArray> {
        readCache(objectKey)?.let { cached -> return Result.success(cached) }

        val originalBytes = remoteSource.download(presignedGetUrl)
            .getOrElse { error -> return Result.failure(error) }
        if (originalBytes.isEmpty()) {
            return Result.failure(IllegalStateException("다운로드한 사진이 비어 있습니다."))
        }
        val previewBytes = withContext(Dispatchers.Default) {
            createRemotePhotoPreview(originalBytes)
        } ?: originalBytes
        writeCache(objectKey, previewBytes)
        return Result.success(previewBytes)
    }

    suspend fun store(objectKey: String, previewBytes: ByteArray) {
        writeCache(objectKey, previewBytes)
    }

    private suspend fun readCache(objectKey: String): ByteArray? = try {
        cache.read(objectKey)
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        null
    }

    private suspend fun writeCache(objectKey: String, previewBytes: ByteArray) {
        try {
            cache.write(objectKey, previewBytes)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            // 캐시 장애는 원격 사진 표시 자체를 실패시키지 않는다.
        }
    }
}

internal fun Throwable.isExpiredPresignedGetUrl(): Boolean =
    this is MapmoryApiException && statusCode == ForbiddenStatusCode

internal expect fun createRemotePhotoPreview(originalBytes: ByteArray): ByteArray?

private const val ForbiddenStatusCode = 403
