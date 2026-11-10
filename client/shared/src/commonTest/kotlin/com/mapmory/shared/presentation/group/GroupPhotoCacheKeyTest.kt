package com.mapmory.shared.presentation.group

import com.mapmory.shared.domain.group.AlbumEntry
import com.mapmory.shared.domain.group.AlbumPhoto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class GroupPhotoCacheKeyTest {
    @Test
    fun signedQueryDoesNotChangeCacheKey() {
        assertEquals(
            groupPhotoCacheKey("https://images.example.com/thumb/photo.webp?signature=old"),
            groupPhotoCacheKey("https://images.example.com/thumb/photo.webp?signature=new"),
        )
    }

    @Test
    fun thumbnailAndOriginalHaveDifferentCacheKeys() {
        assertNotEquals(
            groupPhotoCacheKey("https://images.example.com/thumb/photo.webp?signature=x"),
            groupPhotoCacheKey("https://images.example.com/original/photo.jpg?signature=x"),
        )
        assertNull(groupPhotoCacheKey(null))
    }

    @Test
    fun samePhotoIsIsolatedAcrossAccountGenerationsAndFailedOnlyOncePerGeneration() {
        val failed = mutableSetOf<String>()
        val oldSignature = groupPhotoCacheKey("https://images.example.com/photo.jpg?signature=old", 1)
        val newSignature = groupPhotoCacheKey("https://images.example.com/photo.jpg?signature=new", 1)
        assertEquals(true, failed.add(oldSignature!!))
        assertEquals(false, failed.add(newSignature!!))
        assertNotEquals(oldSignature, groupPhotoCacheKey("https://images.example.com/photo.jpg?signature=new", 2))
    }

    @Test
    fun shortestExpiryIncludesThumbnailAndFallsBackToOriginal() {
        val album = album()
        val entry = AlbumEntry(7, album.opener, null, null, "2026-10-29", null, null, listOf(
            AlbumPhoto(1, "original", 600, 0, "thumbnail", 300),
            AlbumPhoto(2, "other", 450, 1),
        ), "2026-10-29T12:00:00")
        assertEquals(300.seconds, album.copy(entries = listOf(entry)).shortestPhotoUrlLifetime())
        assertEquals(Duration.INFINITE, album.shortestPhotoUrlLifetime())
    }
}
