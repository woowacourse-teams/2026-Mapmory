package com.mapmory.shared.domain.model

/** Complete photo IDs of one personal record, supplied by record detail (never an album page). */
class RecordPhotos(recordId: Long, photoIds: List<String>) {
    val recordId: Long = recordId
    private val snapshot = photoIds.toList()
    val photoIds: List<String> get() = snapshot.toList()

    init {
        require(snapshot.isNotEmpty())
        require(snapshot.all { it.isNotBlank() } && snapshot.distinct().size == snapshot.size)
    }

    /** Ownership is validated by the authenticated record operation before invoking this rule. */
    fun remove(photoId: String): RecordPhotoRemoval {
        require(photoId in snapshot) { "Photo does not belong to this record" }
        val remaining = snapshot - photoId
        return if (remaining.isEmpty()) RecordPhotoRemoval.RecordDeleted(recordId)
        else RecordPhotoRemoval.PhotoRemoved(RecordPhotos(recordId, remaining))
    }
}

sealed interface RecordPhotoRemoval {
    data class RecordDeleted(val recordId: Long) : RecordPhotoRemoval
    data class PhotoRemoved(val record: RecordPhotos) : RecordPhotoRemoval
}
