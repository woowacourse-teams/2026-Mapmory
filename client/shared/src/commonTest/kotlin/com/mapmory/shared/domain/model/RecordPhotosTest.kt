package com.mapmory.shared.domain.model

import kotlin.test.*

class RecordPhotosTest {
    @Test fun `only the last personal record photo deletes the record`() {
        val source = mutableListOf("a", "b")
        val record = RecordPhotos(7, source)
        source.clear()
        val first = assertIs<RecordPhotoRemoval.PhotoRemoved>(record.remove("a"))
        assertEquals(listOf("b"), first.record.photoIds)
        assertEquals(listOf("a", "b"), record.photoIds)
        assertEquals(RecordPhotoRemoval.RecordDeleted(7), first.record.remove("b"))
    }

    @Test fun `absent and duplicate photos cannot cause record deletion`() {
        assertFailsWith<IllegalArgumentException> { RecordPhotos(7, listOf("a", "a")) }
        assertFailsWith<IllegalArgumentException> { RecordPhotos(7, emptyList()) }
        assertFailsWith<IllegalArgumentException> { RecordPhotos(7, listOf("a")).remove("missing") }
    }
}
