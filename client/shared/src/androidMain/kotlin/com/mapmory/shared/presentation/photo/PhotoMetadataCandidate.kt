package com.mapmory.shared.presentation.photo

internal data class PhotoMetadataCandidate(
    val mediaId: Long,
    val contentUri: String,
    val displayName: String,
    val capturedAtMillis: Long?,
    val modifiedAtSeconds: Long,
    val mimeType: String?,
    val sizeBytes: Long,
    val width: Int,
    val height: Int,
)
