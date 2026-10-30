package com.mapmory.shared.presentation.photo

/** Input is chronological (either direction). null matches means no GPS, not another region. */
internal data class TripPhotoCandidate<T>(
    val value: T,
    val capturedAtMillis: Long?,
    val matchesRegion: Boolean?,
)

internal fun <T> selectTripPhotos(candidates: List<TripPhotoCandidate<T>>): List<T> = buildList {
    var previousLocatedMatches = false
    val pending = mutableListOf<T>()
    candidates.forEach { candidate ->
        when (candidate.matchesRegion) {
            true -> {
                if (previousLocatedMatches && candidate.capturedAtMillis != null) addAll(pending)
                pending.clear()
                add(candidate.value)
                previousLocatedMatches = candidate.capturedAtMillis != null
            }
            false -> {
                pending.clear()
                previousLocatedMatches = false
            }
            null -> if (previousLocatedMatches && candidate.capturedAtMillis != null) {
                pending += candidate.value
            }
        }
    }
}

/** Keep only the last destination, across editor exits; never retain full-resolution bytes. */
internal class LastPhotoSearchCache<T> {
    private var key: Pair<Long, PhotoRecommendationDateRange?>? = null
    private var value: T? = null

    fun get(locationId: Long, range: PhotoRecommendationDateRange?): T? {
        val requestedKey = locationId to range
        if (key != requestedKey) {
            key = requestedKey
            value = null
        }
        return value
    }

    fun put(locationId: Long, range: PhotoRecommendationDateRange?, result: T) {
        // An older cancelled request must not replace a newer destination's results.
        if (key == locationId to range) value = result
    }
}
