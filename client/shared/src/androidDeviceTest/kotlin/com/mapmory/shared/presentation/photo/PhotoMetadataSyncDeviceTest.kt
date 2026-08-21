package com.mapmory.shared.presentation.photo

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.mapmory.shared.data.local.photo.PhotoMetadataDatabase
import com.mapmory.shared.data.local.photo.PhotoMetadataEntity
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class PhotoMetadataSyncDeviceTest {
    private lateinit var database: PhotoMetadataDatabase

    @BeforeTest
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            PhotoMetadataDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @AfterTest
    fun tearDown() {
        database.close()
    }

    @Test
    fun newPhotoReadsExifAndWritesRoom() = runBlocking {
        val exifReads = mutableListOf<String>()
        val result = sync(
            current = listOf(candidate(mediaId = 1L, modifiedAtSeconds = 10L)),
            coordinates = mapOf("content://photo/1" to (37.5 to 127.0)),
            exifReads = exifReads,
        ).sync()

        assertEquals(1, result.exifReadCount)
        assertEquals(0, result.reusedCoordinateCount)
        assertEquals(listOf("content://photo/1"), exifReads)
        assertEquals(37.5, database.photoMetadataDao().getAll().single().latitude)
        assertEquals(127.0, database.photoMetadataDao().getAll().single().longitude)
    }

    @Test
    fun unchangedPhotoReusesCachedCoordinates() = runBlocking {
        val exifReads = mutableListOf<String>()
        val photo = candidate(mediaId = 1L, modifiedAtSeconds = 10L)
        sync(
            current = listOf(photo),
            coordinates = mapOf(photo.contentUri to (37.5 to 127.0)),
            exifReads = exifReads,
        ).sync()
        exifReads.clear()

        val result = sync(
            current = listOf(photo),
            coordinates = mapOf(photo.contentUri to (35.0 to 129.0)),
            exifReads = exifReads,
            scanId = 2L,
        ).sync()

        assertEquals(0, result.exifReadCount)
        assertEquals(1, result.reusedCoordinateCount)
        assertTrue(exifReads.isEmpty())
        assertEquals(37.5, database.photoMetadataDao().getAll().single().latitude)
    }

    @Test
    fun modifiedPhotoReadsExifAgainAndUpdatesCoordinates() = runBlocking {
        val photo = candidate(mediaId = 1L, modifiedAtSeconds = 10L)
        sync(
            current = listOf(photo),
            coordinates = mapOf(photo.contentUri to (37.5 to 127.0)),
            exifReads = mutableListOf(),
        ).sync()

        val exifReads = mutableListOf<String>()
        val result = sync(
            current = listOf(photo.copy(modifiedAtSeconds = 11L)),
            coordinates = mapOf(photo.contentUri to (35.0 to 129.0)),
            exifReads = exifReads,
            scanId = 2L,
        ).sync()

        assertEquals(1, result.exifReadCount)
        assertEquals(0, result.reusedCoordinateCount)
        assertEquals(listOf(photo.contentUri), exifReads)
        assertEquals(35.0, database.photoMetadataDao().getAll().single().latitude)
        assertEquals(129.0, database.photoMetadataDao().getAll().single().longitude)
    }

    @Test
    fun photoMissingFromCurrentSnapshotIsRemoved() = runBlocking {
        val first = candidate(mediaId = 1L, modifiedAtSeconds = 10L)
        val second = candidate(mediaId = 2L, modifiedAtSeconds = 10L)
        sync(
            current = listOf(first, second),
            coordinates = mapOf(
                first.contentUri to (37.5 to 127.0),
                second.contentUri to (35.1 to 129.0),
            ),
            exifReads = mutableListOf(),
        ).sync()

        sync(
            current = listOf(first),
            coordinates = mapOf(first.contentUri to (37.5 to 127.0)),
            exifReads = mutableListOf(),
            scanId = 2L,
        ).sync()

        assertEquals(setOf(1L), database.photoMetadataDao().getAll().map(PhotoMetadataEntity::mediaId).toSet())
    }

    @Test
    fun mediaStoreFailureKeepsPreviousSnapshot() = runBlocking {
        val photo = candidate(mediaId = 1L, modifiedAtSeconds = 10L)
        sync(
            current = listOf(photo),
            coordinates = mapOf(photo.contentUri to (37.5 to 127.0)),
            exifReads = mutableListOf(),
        ).sync()

        sync(
            current = null,
            coordinates = emptyMap(),
            exifReads = mutableListOf(),
            scanId = 2L,
        ).sync()

        assertEquals(setOf(1L), database.photoMetadataDao().getAll().map(PhotoMetadataEntity::mediaId).toSet())
    }

    private fun sync(
        current: List<PhotoMetadataCandidate>?,
        coordinates: Map<String, Pair<Double, Double>>,
        exifReads: MutableList<String>,
        scanId: Long = 1L,
    ): PhotoMetadataSync {
        val dao = database.photoMetadataDao()
        return PhotoMetadataSync(
            readPrevious = { dao.getAll() },
            readCurrent = { current },
            readCoordinates = { contentUri ->
                exifReads += contentUri
                coordinates[contentUri]
            },
            writeSnapshot = { photos, currentScanId -> dao.replaceSnapshot(photos, currentScanId) },
            scanIdProvider = { scanId },
        )
    }

    private fun candidate(
        mediaId: Long,
        modifiedAtSeconds: Long,
    ) = PhotoMetadataCandidate(
        mediaId = mediaId,
        contentUri = "content://photo/$mediaId",
        displayName = "photo-$mediaId.jpg",
        capturedAtMillis = 1_000L,
        modifiedAtSeconds = modifiedAtSeconds,
        mimeType = "image/jpeg",
        sizeBytes = 128L,
        width = 100,
        height = 100,
    )
}
