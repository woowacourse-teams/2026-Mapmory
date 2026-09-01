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
    fun `새_사진의_EXIF를_읽고_Room에_저장한다`() = runBlocking {
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
    fun `변경되지_않은_사진은_캐시된_좌표를_재사용한다`() = runBlocking {
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
    fun `변경된_사진은_EXIF를_다시_읽고_좌표를_갱신한다`() = runBlocking {
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
    fun `GPS가_없는_사진은_좌표를_얻을_때까지_다시_시도한다`() = runBlocking {
        val photo = candidate(mediaId = 1L, modifiedAtSeconds = 10L)
        val firstResult = sync(
            current = listOf(photo),
            coordinates = emptyMap(),
            exifReads = mutableListOf(),
        ).sync()

        assertEquals(1, firstResult.exifReadCount)
        assertEquals(null, database.photoMetadataDao().getAll().single().latitude)

        val exifReads = mutableListOf<String>()
        val secondResult = sync(
            current = listOf(photo),
            coordinates = mapOf(photo.contentUri to (35.1 to 129.0)),
            exifReads = exifReads,
            scanId = 2L,
        ).sync()

        assertEquals(1, secondResult.exifReadCount)
        assertEquals(0, secondResult.reusedCoordinateCount)
        assertEquals(listOf(photo.contentUri), exifReads)
        assertEquals(35.1, database.photoMetadataDao().getAll().single().latitude)
        assertEquals(129.0, database.photoMetadataDao().getAll().single().longitude)
    }

    @Test
    fun `혼합_스냅샷에서는_변경되지_않은_위치_사진만_재사용한다`() = runBlocking {
        val unchanged = candidate(mediaId = 1L, modifiedAtSeconds = 10L)
        val changed = candidate(mediaId = 2L, modifiedAtSeconds = 10L)
        sync(
            current = listOf(unchanged, changed),
            coordinates = mapOf(
                unchanged.contentUri to (37.5 to 127.0),
                changed.contentUri to (35.1 to 129.0),
            ),
            exifReads = mutableListOf(),
        ).sync()

        val exifReads = mutableListOf<String>()
        val result = sync(
            current = listOf(unchanged, changed.copy(modifiedAtSeconds = 11L)),
            coordinates = mapOf(changed.contentUri to (35.2 to 129.1)),
            exifReads = exifReads,
            scanId = 2L,
        ).sync()

        assertEquals(1, result.reusedCoordinateCount)
        assertEquals(1, result.exifReadCount)
        assertEquals(listOf(changed.contentUri), exifReads)
        val stored = database.photoMetadataDao().getAll().associateBy(PhotoMetadataEntity::mediaId)
        assertEquals(37.5, stored.getValue(unchanged.mediaId).latitude)
        assertEquals(35.2, stored.getValue(changed.mediaId).latitude)
    }

    @Test
    fun `현재_스냅샷에_없는_사진은_삭제한다`() = runBlocking {
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
    fun `빈_MediaStore_스냅샷은_Room을_비운다`() = runBlocking {
        val photo = candidate(mediaId = 1L, modifiedAtSeconds = 10L)
        sync(
            current = listOf(photo),
            coordinates = mapOf(photo.contentUri to (37.5 to 127.0)),
            exifReads = mutableListOf(),
        ).sync()

        sync(
            current = emptyList(),
            coordinates = emptyMap(),
            exifReads = mutableListOf(),
            scanId = 2L,
        ).sync()

        assertTrue(database.photoMetadataDao().getAll().isEmpty())
    }

    @Test
    fun `Room_조회는_위치가_있는_사진만_필터링하고_촬영_시각순으로_정렬한다`() = runBlocking {
        val newest = candidate(mediaId = 1L, modifiedAtSeconds = 10L, capturedAtMillis = 300L)
        val withoutGps = candidate(mediaId = 2L, modifiedAtSeconds = 10L, capturedAtMillis = 200L)
        val oldest = candidate(mediaId = 3L, modifiedAtSeconds = 10L, capturedAtMillis = 100L)
        sync(
            current = listOf(newest, withoutGps, oldest),
            coordinates = mapOf(
                newest.contentUri to (37.5 to 127.0),
                oldest.contentUri to (35.1 to 129.0),
            ),
            exifReads = mutableListOf(),
        ).sync()

        val dao = database.photoMetadataDao()
        assertEquals(
            listOf(1L, 3L),
            dao.getLocatedPhotos().map(PhotoMetadataEntity::mediaId),
        )
        assertEquals(
            listOf(1L, 2L),
            dao.getPhotosCapturedBetween(150L, 350L).map(PhotoMetadataEntity::mediaId),
        )
    }

    @Test
    fun `MediaStore_조회_실패_시_기존_스냅샷을_유지한다`() = runBlocking {
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
        capturedAtMillis: Long? = 1_000L,
    ) = PhotoMetadataCandidate(
        mediaId = mediaId,
        contentUri = "content://photo/$mediaId",
        displayName = "photo-$mediaId.jpg",
        capturedAtMillis = capturedAtMillis,
        modifiedAtSeconds = modifiedAtSeconds,
        mimeType = "image/jpeg",
        sizeBytes = 128L,
        width = 100,
        height = 100,
    )
}
