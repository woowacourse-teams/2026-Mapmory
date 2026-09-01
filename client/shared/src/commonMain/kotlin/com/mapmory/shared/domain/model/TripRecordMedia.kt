package com.mapmory.shared.domain.model

// 여행 기록에 연결된 미디어 메타데이터다.
data class TripRecordMedia(
    val id: Long,
    val objectKey: String,
    val sortOrder: Int,
    val url: String?,
    // 서버 업로드가 연결되기 전에도 선택한 사진의 로컬 미리보기를 유지한다.
    val previewBytes: ByteArray? = null,
    val originalBytes: ByteArray? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val capturedAt: String? = null,
)

data class TripRecordMediaDraft(
    val objectKey: String,
    val sortOrder: Int,
    val previewBytes: ByteArray?,
    val originalBytes: ByteArray? = null,
    val fileName: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val capturedAt: String? = null,
)
