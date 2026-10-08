package com.mapmory.shared.data.media

/**
 * 선택한 사진의 원본을 플랫폼 로컬 식별자로부터 필요할 때만 읽는다.
 *
 * UI 상태에는 원본 바이트를 보관하지 않고, 업로드 직전에 한 장씩 읽어 메모리 사용량을
 * 선택한 사진 수와 무관하게 제한한다.
 */
fun interface LocalPhotoDataSource {
    suspend fun read(localId: String): ByteArray?

    /** 원본 전체를 메모리에 올리지 않고 로컬 사진의 촬영일만 읽는다. */
    suspend fun capturedAt(localId: String): String? = null
}
