package com.mapmory.shared.presentation.photo

import com.mapmory.shared.data.local.photo.PhotoMetadataEntity
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

internal class PhotoMetadataSync(
    private val readPrevious: suspend () -> List<PhotoMetadataEntity>,
    private val readCurrent: suspend () -> List<PhotoMetadataCandidate>?,
    private val readCoordinates: suspend (String) -> Pair<Double, Double>?,
    private val writeSnapshot: suspend (List<PhotoMetadataEntity>, Long) -> Unit,
    private val scanIdProvider: () -> Long = { System.currentTimeMillis() },
) {
    suspend fun sync(
        onProgress: (processed: Int, total: Int) -> Unit = { _, _ -> },
    ): PhotoMetadataSyncResult {
        val previousPhotos = readPrevious()
        val previousById = previousPhotos.associateBy(PhotoMetadataEntity::mediaId)
        val currentPhotos = readCurrent() ?: return PhotoMetadataSyncResult(
            photos = emptyList(),
            exifReadCount = 0,
            reusedCoordinateCount = 0,
            previousPhotoCount = previousPhotos.size,
        )
        val scanId = scanIdProvider()
        val total = currentPhotos.size
        val progressStep = (total / 100).coerceAtLeast(1)
        var processed = 0
        var exifReadCount = 0
        var reusedCoordinateCount = 0
        val photos = currentPhotos.map { candidate ->
            coroutineContext.ensureActive()
            val previous = previousById[candidate.mediaId]
            val previousCoordinates = previous?.let { cached ->
                cached.latitude?.let { latitude ->
                    cached.longitude?.let { longitude -> latitude to longitude }
                }
            }
            val coordinates = if (
                shouldReuseCoordinates(
                    previousModifiedAtSeconds = previous?.modifiedAtSeconds,
                    previousLatitude = previous?.latitude,
                    previousLongitude = previous?.longitude,
                    currentModifiedAtSeconds = candidate.modifiedAtSeconds,
                )
            ) {
                reusedCoordinateCount++
                requireNotNull(previousCoordinates)
            } else {
                exifReadCount++
                readCoordinates(candidate.contentUri)
            }
            coroutineContext.ensureActive()
            processed++
            if (processed == total || processed % progressStep == 0) {
                onProgress(processed, total)
            }
            PhotoMetadataEntity(
                mediaId = candidate.mediaId,
                contentUri = candidate.contentUri,
                displayName = candidate.displayName,
                capturedAtMillis = candidate.capturedAtMillis,
                modifiedAtSeconds = candidate.modifiedAtSeconds,
                latitude = coordinates?.first,
                longitude = coordinates?.second,
                mimeType = candidate.mimeType,
                sizeBytes = candidate.sizeBytes,
                width = candidate.width,
                height = candidate.height,
                scanId = scanId,
            )
        }
        coroutineContext.ensureActive()
        writeSnapshot(photos, scanId)
        return PhotoMetadataSyncResult(
            photos = photos,
            exifReadCount = exifReadCount,
            reusedCoordinateCount = reusedCoordinateCount,
            previousPhotoCount = previousPhotos.size,
        )
    }
}
