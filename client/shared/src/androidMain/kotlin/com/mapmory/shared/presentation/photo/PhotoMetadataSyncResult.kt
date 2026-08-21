package com.mapmory.shared.presentation.photo

import com.mapmory.shared.data.local.photo.PhotoMetadataEntity

internal data class PhotoMetadataSyncResult(
    val photos: List<PhotoMetadataEntity>,
    val exifReadCount: Int,
    val reusedCoordinateCount: Int,
    val previousPhotoCount: Int,
)
