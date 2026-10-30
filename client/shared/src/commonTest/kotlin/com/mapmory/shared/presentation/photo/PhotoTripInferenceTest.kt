package com.mapmory.shared.presentation.photo

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PhotoTripInferenceTest {
    @Test
    fun includesUndatedLocationBetweenTwoMatchingAnchors() {
        assertEquals(listOf("16", "15", "14"), selectTripPhotos(listOf(
            TripPhotoCandidate("16", 16L, true),
            TripPhotoCandidate("15", 15L, null),
            TripPhotoCandidate("14", 14L, true),
        )))
    }

    @Test
    fun otherRegionBreaksInferenceAndUnboundedPhotosAreExcluded() {
        assertEquals(listOf("16", "12"), selectTripPhotos(listOf(
            TripPhotoCandidate("17", 17L, null),
            TripPhotoCandidate("16", 16L, true),
            TripPhotoCandidate("15", 15L, null),
            TripPhotoCandidate("14", 14L, false),
            TripPhotoCandidate("13", 13L, null),
            TripPhotoCandidate("12", 12L, true),
            TripPhotoCandidate("11", 11L, null),
        )))
    }

    @Test
    fun missingCaptureTimeIsNotInferred() {
        assertEquals(listOf("a", "b"), selectTripPhotos(listOf(
            TripPhotoCandidate("a", 16L, true),
            TripPhotoCandidate("unknown", null, null),
            TripPhotoCandidate("b", 14L, true),
        )))
    }

    @Test
    fun sameDestinationReusesResultsButChangingDestinationInvalidatesThem() {
        val cache = LastPhotoSearchCache<List<String>>()
        assertNull(cache.get(1, null))
        cache.put(1, null, listOf("photo"))
        assertEquals(listOf("photo"), cache.get(1, null))
        assertNull(cache.get(2, null))
        cache.put(1, null, listOf("stale"))
        assertNull(cache.get(2, null))
        assertNull(cache.get(1, null))
    }
}
