package com.mapmory.shared.data.media

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Presigned URL이 바뀌어도 같은 사진을 재사용하도록 Object Key를 키로 삼는 캐시 경계다. */
interface PhotoPreviewCache {
    suspend fun read(objectKey: String): ByteArray?

    suspend fun write(objectKey: String, bytes: ByteArray)
}

/** 테스트와 별도 플랫폼 저장소가 없는 환경에서 사용하는 제한된 메모리 캐시다. */
class MemoryPhotoPreviewCache(
    private val maxBytes: Long = DefaultMemoryCacheBytes,
) : PhotoPreviewCache {
    private val mutex = Mutex()
    private val values = linkedMapOf<String, ByteArray>()
    private var currentBytes = 0L

    init {
        require(maxBytes > 0) { "사진 메모리 캐시 크기는 양수여야 합니다." }
    }

    override suspend fun read(objectKey: String): ByteArray? = mutex.withLock {
        values.remove(objectKey)?.let { bytes ->
            values[objectKey] = bytes
            bytes.copyOf()
        }
    }

    override suspend fun write(objectKey: String, bytes: ByteArray) {
        if (objectKey.isBlank() || bytes.isEmpty() || bytes.size > maxBytes) return
        mutex.withLock {
            values.remove(objectKey)?.let { previous -> currentBytes -= previous.size }
            values[objectKey] = bytes.copyOf()
            currentBytes += bytes.size
            while (currentBytes > maxBytes && values.isNotEmpty()) {
                val oldestKey = values.keys.first()
                currentBytes -= requireNotNull(values.remove(oldestKey)).size
            }
        }
    }
}

internal fun objectKeyCacheFileName(objectKey: String): String {
    require(objectKey.isNotBlank()) { "사진 정보를 확인하지 못했습니다." }
    // v1에는 Android 원격 사진의 EXIF 방향이 적용되지 않았으므로 기존 파일을 재사용하지 않는다.
    val bytes = "$PreviewCacheSchemaVersion:$objectKey".encodeToByteArray()
    val forward = bytes.fold(FnvOffsetBasis) { hash, byte ->
        (hash xor (byte.toLong() and UnsignedByteMask)) * FnvPrime
    }
    val backward = bytes.reversed().fold(FnvOffsetBasis) { hash, byte ->
        (hash xor (byte.toLong() and UnsignedByteMask)) * FnvPrime
    }
    return forward.toULong().toString(HexRadix).padStart(HashPartLength, '0') +
        backward.toULong().toString(HexRadix).padStart(HashPartLength, '0')
}

private const val DefaultMemoryCacheBytes = 16L * 1024 * 1024
private const val PreviewCacheSchemaVersion = 2
private const val FnvOffsetBasis = -3750763034362895579L
private const val FnvPrime = 1099511628211L
private const val UnsignedByteMask = 0xFFL
private const val HexRadix = 16
private const val HashPartLength = 16
