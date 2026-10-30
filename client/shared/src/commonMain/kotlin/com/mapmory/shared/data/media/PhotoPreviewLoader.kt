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
    suspend fun capturedAt(objectKey: String): String? = try {
        cache.capturedAt(objectKey)
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        null
    }

    suspend fun rememberCapturedAt(objectKey: String, capturedAt: String) {
        try {
            cache.rememberCapturedAt(objectKey, capturedAt)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            // 메타데이터 캐시 실패가 서버 저장 성공을 실패로 바꾸지 않는다.
        }
    }

    suspend fun cachedUri(objectKey: String): String? = cache.uri(objectKey)

    suspend fun cachedForDisplay(objectKey: String): PhotoPreviewDisplay? =
        cache.uri(objectKey)?.let { uri -> PhotoPreviewDisplay(uri = uri) }
            ?: readCache(objectKey)?.let { bytes -> PhotoPreviewDisplay(bytes = bytes) }

    suspend fun copyCached(fromObjectKey: String, toObjectKey: String): String? {
        cache.copy(fromObjectKey, toObjectKey)
        return cache.uri(toObjectKey)
    }

    suspend fun rememberLocalSource(objectKey: String, localSourceKey: String) {
        try {
            cache.linkLocalSource(objectKey, localSourceKey)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            // 로컬 식별자 캐시 장애가 기록 저장·표시를 막지 않도록 한다.
        }
    }

    suspend fun localSourceKey(objectKey: String): String? = try {
        cache.localSourceKey(objectKey)
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        null
    }

    suspend fun loadForDisplay(
        objectKey: String,
        presignedGetUrl: String,
    ): Result<PhotoPreviewDisplay> {
        cachedForDisplay(objectKey)?.let { cached -> return Result.success(cached) }
        return load(objectKey, presignedGetUrl).map { bytes ->
            cache.uri(objectKey)?.let { uri -> PhotoPreviewDisplay(uri = uri) }
                ?: PhotoPreviewDisplay(bytes = bytes)
        }
    }

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

internal data class PhotoPreviewDisplay(
    val uri: String? = null,
    val bytes: ByteArray? = null,
)

internal fun Throwable.isExpiredPresignedGetUrl(): Boolean =
    this is MapmoryApiException && statusCode == ForbiddenStatusCode

internal expect fun createRemotePhotoPreview(originalBytes: ByteArray): ByteArray?

private const val ForbiddenStatusCode = 403
