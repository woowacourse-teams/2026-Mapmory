package com.mapmory.shared.domain

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotSame
import kotlin.test.assertSame

class TripRecordsTest {
    @Test
    fun `여행_기록을_추가한다`() {
        val tripRecords = TripRecords()

        val result = tripRecords.addTripRecord(
            imageUri = "image.jpg",
            tripRecordTitle = "제주도 여행",
            tripRecordDescription = "성산일출봉을 다녀왔다",
            tripLocation = "제주도",
            startTripDate = LocalDate(2026, 8, 1),
            endTripDate = LocalDate(2026, 8, 3),
        )

        assertEquals(0, tripRecords.tripRecords.size)
        assertEquals(1, result.tripRecords.size)
        assertEquals("제주도 여행", result.tripRecords.single().tripRecordTitle)
    }

    @Test
    fun `시작일이_종료일보다_늦은_여행_기록은_추가할_수_없다`() {
        val tripRecords = TripRecords()

        assertFailsWith<IllegalArgumentException> {
            tripRecords.addTripRecord(
                imageUri = "image.jpg",
                tripRecordTitle = "잘못된 여행",
                tripRecordDescription = null,
                tripLocation = "제주도",
                startTripDate = LocalDate(2026, 8, 8),
                endTripDate = LocalDate(2026, 8, 7),
            )
        }
        assertEquals(emptyList(), tripRecords.tripRecords)
    }

    @Test
    fun `여행_기록을_삭제한다`() {
        val record = createTripRecord()
        val tripRecords = TripRecords(listOf(record))

        val result = tripRecords.removeTripRecord(record)

        assertEquals(emptyList(), result.tripRecords)
    }

    @Test
    fun `존재하지_않는_여행_기록을_삭제하면_기존_객체를_반환한다`() {
        val tripRecords = TripRecords(listOf(createTripRecord(id = 1L)))

        val result = tripRecords.removeTripRecord(createTripRecord(id = 2L))

        assertSame(tripRecords, result)
    }

    @Test
    fun `빈_목록에서_여행_기록을_삭제하면_기존_객체를_반환한다`() {
        val tripRecords = TripRecords()

        val result = tripRecords.removeTripRecord(createTripRecord())

        assertSame(tripRecords, result)
    }

    @Test
    fun `내용이_달라도_ID가_같은_여행_기록을_삭제한다`() {
        val storedRecord = createTripRecord(id = 1L, title = "저장된 제목")
        val deletingRecord = createTripRecord(id = 1L, title = "변경된 제목")
        val tripRecords = TripRecords(listOf(storedRecord))

        val result = tripRecords.removeTripRecord(deletingRecord)

        assertEquals(emptyList(), result.tripRecords)
    }

    @Test
    fun `여행_기록의_전달된_필드만_수정한다`() {
        val record = createTripRecord()
        val tripRecords = TripRecords(listOf(record))

        val result = tripRecords.editTripRecord(
            editingRecord = record,
            editingImage = null,
            editingTitle = "수정된 제목",
            editingDescription = null,
            editingStartTripDate = null,
            editingEndTripDate = null,
            editingLocation = "부산",
        )

        val editedRecord = result.tripRecords.single()
        assertNotSame(tripRecords, result)
        assertEquals(record.id, editedRecord.id)
        assertEquals(record.imageUrl, editedRecord.imageUrl)
        assertEquals("수정된 제목", editedRecord.tripRecordTitle)
        assertEquals(record.tripRecordDescription, editedRecord.tripRecordDescription)
        assertEquals("부산", editedRecord.location)
        assertEquals(record.startTripDate, editedRecord.startTripDate)
        assertEquals(record.endTripDate, editedRecord.endTripDate)
    }

    @Test
    fun `여행_기록을_수정해도_목록_순서는_유지된다`() {
        val firstRecord = createTripRecord(id = 1L, title = "첫 번째")
        val secondRecord = createTripRecord(id = 2L, title = "두 번째")
        val tripRecords = TripRecords(listOf(firstRecord, secondRecord))

        val result = tripRecords.editTripRecord(
            editingRecord = firstRecord,
            editingImage = null,
            editingTitle = "수정된 첫 번째",
            editingDescription = null,
            editingStartTripDate = null,
            editingEndTripDate = null,
            editingLocation = null,
        )

        assertEquals(listOf(firstRecord.id, secondRecord.id), result.tripRecords.map { it.id })
        assertEquals("수정된 첫 번째", result.tripRecords.first().tripRecordTitle)
    }

    @Test
    fun `수정할_필드가_없으면_기록_내용을_유지한다`() {
        val record = createTripRecord()
        val tripRecords = TripRecords(listOf(record))

        val result = tripRecords.editTripRecord(
            editingRecord = record,
            editingImage = null,
            editingTitle = null,
            editingDescription = null,
            editingStartTripDate = null,
            editingEndTripDate = null,
            editingLocation = null,
        )

        assertEquals(record, result.tripRecords.single())
        assertEquals(record, tripRecords.tripRecords.single())
    }

    @Test
    fun `여행_시작일과_종료일을_함께_수정한다`() {
        val record = createTripRecord()
        val tripRecords = TripRecords(listOf(record))
        val editingStartTripDate = LocalDate(2026, 9, 1)
        val editingEndTripDate = LocalDate(2026, 9, 5)

        val result = tripRecords.editTripRecord(
            editingRecord = record,
            editingImage = null,
            editingTitle = null,
            editingDescription = null,
            editingStartTripDate = editingStartTripDate,
            editingEndTripDate = editingEndTripDate,
            editingLocation = null,
        )

        val editedRecord = result.tripRecords.single()
        assertEquals(editingStartTripDate, editedRecord.startTripDate)
        assertEquals(editingEndTripDate, editedRecord.endTripDate)
    }

    @Test
    fun `시작일만_수정하면_기존_종료일을_유지한다`() {
        val record = createTripRecord()
        val tripRecords = TripRecords(listOf(record))
        val editingStartTripDate = LocalDate(2026, 8, 2)

        val result = tripRecords.editTripRecord(
            editingRecord = record,
            editingImage = null,
            editingTitle = null,
            editingDescription = null,
            editingStartTripDate = editingStartTripDate,
            editingEndTripDate = null,
            editingLocation = null,
        )

        val editedRecord = result.tripRecords.single()
        assertEquals(editingStartTripDate, editedRecord.startTripDate)
        assertEquals(record.endTripDate, editedRecord.endTripDate)
    }

    @Test
    fun `수정한_시작일이_종료일보다_늦으면_예외가_발생한다`() {
        val record = createTripRecord()
        val tripRecords = TripRecords(listOf(record))

        assertFailsWith<IllegalArgumentException> {
            tripRecords.editTripRecord(
                editingRecord = record,
                editingImage = null,
                editingTitle = null,
                editingDescription = null,
                editingStartTripDate = LocalDate(2026, 8, 4),
                editingEndTripDate = null,
                editingLocation = null,
            )
        }
        assertEquals(record, tripRecords.tripRecords.single())
    }

    @Test
    fun `수정한_종료일이_시작일보다_빠르면_예외가_발생한다`() {
        val record = createTripRecord()
        val tripRecords = TripRecords(listOf(record))

        assertFailsWith<IllegalArgumentException> {
            tripRecords.editTripRecord(
                editingRecord = record,
                editingImage = null,
                editingTitle = null,
                editingDescription = null,
                editingStartTripDate = null,
                editingEndTripDate = LocalDate(2026, 7, 31),
                editingLocation = null,
            )
        }
        assertEquals(record, tripRecords.tripRecords.single())
    }

    @Test
    fun `존재하지_않는_여행_기록을_수정하면_예외가_발생한다`() {
        val tripRecords = TripRecords(listOf(createTripRecord(id = 1L)))

        assertFailsWith<IllegalArgumentException> {
            tripRecords.editTripRecord(
                editingRecord = createTripRecord(id = 2L),
                editingImage = null,
                editingTitle = "수정된 제목",
                editingDescription = null,
                editingStartTripDate = null,
                editingEndTripDate = null,
                editingLocation = null,
            )
        }
    }

    @Test
    fun `생성자에_전달한_목록이_바뀌어도_여행_기록_목록은_바뀌지_않는다`() {
        val source = mutableListOf(createTripRecord(id = 1L))
        val tripRecords = TripRecords(source)

        source += createTripRecord(id = 2L)

        assertEquals(listOf(1L), tripRecords.tripRecords.map { it.id })
    }

    private fun createTripRecord(
        id: Long = 1L,
        title: String = "제주도 여행",
    ): TripRecord = TripRecord(
        id = id,
        imageUrl = "image.jpg",
        tripRecordTitle = title,
        tripRecordDescription = "여행 기록",
        startTripDate = LocalDate(2026, 8, 1),
        endTripDate = LocalDate(2026, 8, 3),
        location = "제주도",
    )
}
