package com.mapmory.shared.data.media

import com.mapmory.shared.domain.model.TripRecordMediaDraft

/**
 * 업로드한 사진의 원본으로 기기 미리보기 캐시를 큰 미리보기로 바꾼다(#686).
 * 업로드 때 원본이 이미 메모리에 있으므로 다시 받지 않는다. 원본을 따로 받아야 하는 플랫폼(iOS)만 주입한다.
 *
 * @return 큰 미리보기로 바꿨으면 true. false면 기존 미리보기를 그대로 쓴다.
 */
fun interface SavedPhotoPreviewWriter {
    suspend fun write(localId: String, objectKey: String, originalBytes: ByteArray): Boolean

    /**
     * 이미 올라간 전송을 건너뛸 때, 원본 바이트 대신 남아 있는 업로드 파일로 같은 교체를 한다(#693).
     * 파일을 디스크에서 바로 디코딩하므로 원본을 다시 받거나 메모리에 올리지 않는다.
     */
    suspend fun writeFromFile(localId: String, objectKey: String, path: String): Boolean = false
}

/**
 * 큰 미리보기로 바꾼 사진은 초안의 작은 미리보기 바이트를 넘기지 않는다.
 * 넘기면 저장 뒤 캐시 단계(`CachedMediaTripRecordRepository`)가 그 바이트로 큰 미리보기를 다시 덮어쓴다.
 */
internal fun List<TripRecordMediaDraft>.withoutReplacedPreviews(
    replacedLocalIds: Set<String>,
): List<TripRecordMediaDraft> {
    if (replacedLocalIds.isEmpty()) return this
    return map { media -> if (media.objectKey in replacedLocalIds) media.copy(previewBytes = null) else media }
}
