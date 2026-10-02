package com.mapmory.shared.presentation.photo

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PhotoRecommendationPagingTest {
    @Test
    fun `추천_사진은_24장_단위로_누적된다`() {
        val pages = listOf(
            PhotoRecommendationPage(1, (1..24).map { photo(it) }, hasMore = true),
            PhotoRecommendationPage(1, (25..48).map { photo(it) }, hasMore = true),
            PhotoRecommendationPage(1, (49..72).map { photo(it) }, hasMore = true),
            PhotoRecommendationPage(1, (73..96).map { photo(it) }, hasMore = true),
            PhotoRecommendationPage(1, (97..120).map { photo(it) }, hasMore = false),
        )

        val first = PhotoRecommendationPagingState().accept(pages[0])
        val second = first?.accept(pages[1])
        val third = second?.accept(pages[2])
        val fourth = third?.accept(pages[3])
        val fifth = fourth?.accept(pages[4])

        assertNotNull(first)
        assertNotNull(second)
        assertNotNull(third)
        assertNotNull(fourth)
        assertNotNull(fifth)
        assertEquals(24, first.photos.size)
        assertEquals(48, second.photos.size)
        assertEquals(72, third.photos.size)
        assertEquals(96, fourth.photos.size)
        assertEquals(120, fifth.photos.size)
        assertEquals(4, fifth.pageIndex)
        assertFalse(fifth.hasMore)
        assertEquals(120, fifth.photos.map(SelectedPhoto::id).toSet().size)
        assertEquals(100, fifth.selectedIds.size)
    }

    @Test
    fun `추천_사진은_남은_자리보다_많이_선택할_수_없다`() {
        val initial = PhotoRecommendationPagingState(maxSelectionCount = 3)
            .accept(
                PhotoRecommendationPage(
                    generation = 1,
                    photos = (1..5).map(::photo),
                    hasMore = false,
                ),
            )

        assertNotNull(initial)
        assertEquals(setOf("1", "2", "3"), initial.selectedIds)
        assertEquals(initial, initial.toggleSelection("4"))

        val replaced = initial
            .toggleSelection("1")
            .toggleSelection("4")
        assertEquals(setOf("2", "3", "4"), replaced.selectedIds)
    }

    @Test
    fun `드래그_선택은_지나간_사진을_같은_상태로_변경한다`() {
        val initial = requireNotNull(
            PhotoRecommendationPagingState(maxSelectionCount = 3).accept(
                page = PhotoRecommendationPage(
                    generation = 1,
                    photos = (1..4).map(::photo),
                    hasMore = false,
                ),
                autoSelectNewPhotos = false,
            ),
        )

        val selected = initial
            .setSelection("1", selected = true)
            .setSelection("2", selected = true)
            .setSelection("3", selected = true)
            .setSelection("4", selected = true)

        assertEquals(setOf("1", "2", "3"), selected.selectedIds)

        val deselected = selected
            .setSelection("1", selected = false)
            .setSelection("2", selected = false)

        assertEquals(setOf("3"), deselected.selectedIds)
    }

    @Test
    fun `새_앨범_플로우는_추천_사진을_자동으로_선택하지_않는다`() {
        val result = PhotoRecommendationPagingState(maxSelectionCount = 3)
            .accept(
                page = PhotoRecommendationPage(
                    generation = 1,
                    photos = (1..5).map(::photo),
                    hasMore = false,
                ),
                autoSelectNewPhotos = false,
            )

        assertNotNull(result)
        assertEquals(5, result.photos.size)
        assertTrue(result.selectedIds.isEmpty())
    }

    @Test
    fun `빈_추가_페이지는_기존_사진과_선택을_보존한다`() {
        val first = PhotoRecommendationPagingState()
            .accept(PhotoRecommendationPage(1, listOf(photo(1), photo(2)), hasMore = true))
            ?.toggleSelection("1")

        val result = first?.accept(PhotoRecommendationPage(1, emptyList(), hasMore = false))

        assertNotNull(result)
        assertEquals(listOf("1", "2"), result.photos.map(SelectedPhoto::id))
        assertEquals(setOf("2"), result.selectedIds)
        assertFalse(result.hasMore)
        assertEquals(0, result.pageIndex)
    }

    @Test
    fun `이전_generation_페이지는_무시된다`() {
        val current = PhotoRecommendationPagingState()
            .accept(PhotoRecommendationPage(2, listOf(photo(1)), hasMore = false))

        val result = current?.accept(
            PhotoRecommendationPage(1, listOf(photo(2)), hasMore = false),
        )

        assertNotNull(current)
        assertEquals(null, result)
    }

    @Test
    fun `중복_페이지는_중복_사진을_추가하지_않는다`() {
        val first = PhotoRecommendationPagingState()
            .accept(PhotoRecommendationPage(1, listOf(photo(1), photo(2)), hasMore = true))
        val result = first?.accept(
            PhotoRecommendationPage(1, listOf(photo(1), photo(2)), hasMore = false),
        )

        assertNotNull(result)
        assertEquals(listOf("1", "2"), result.photos.map(SelectedPhoto::id))
        assertFalse(result.hasMore)
        assertEquals(0, result.pageIndex)
    }

    @Test
    fun `선택_상태는_페이지_추가_후에도_유지된다`() {
        val first = PhotoRecommendationPagingState()
            .accept(PhotoRecommendationPage(1, listOf(photo(1), photo(2)), hasMore = true))
            ?.toggleSelection("1")
        val result = first?.accept(
            PhotoRecommendationPage(1, listOf(photo(3)), hasMore = false),
        )

        assertNotNull(result)
        assertEquals(setOf("2", "3"), result.selectedIds)
    }

    @Test
    fun `같은_하단_이벤트는_한번만_허용된다`() {
        val firstKey = RecommendationLoadKey(generation = 1, visibleCount = 24)

        assertTrue(
            shouldLoadNextRecommendationPage(
                isAtBottom = true,
                isLoading = false,
                hasMore = true,
                lastTriggerKey = null,
                currentKey = firstKey,
            ),
        )
        assertFalse(
            shouldLoadNextRecommendationPage(
                isAtBottom = true,
                isLoading = false,
                hasMore = true,
                lastTriggerKey = firstKey,
                currentKey = firstKey,
            ),
        )
        assertFalse(
            shouldLoadNextRecommendationPage(
                isAtBottom = true,
                isLoading = true,
                hasMore = true,
                lastTriggerKey = null,
                currentKey = firstKey,
            ),
        )
        assertFalse(
            shouldLoadNextRecommendationPage(
                isAtBottom = true,
                isLoading = false,
                hasMore = false,
                lastTriggerKey = null,
                currentKey = firstKey,
            ),
        )
        assertTrue(
            shouldLoadNextRecommendationPage(
                isAtBottom = true,
                isLoading = false,
                hasMore = true,
                lastTriggerKey = firstKey,
                currentKey = RecommendationLoadKey(generation = 1, visibleCount = 48),
            ),
        )
    }

    private fun photo(id: Int) = SelectedPhoto(
        id = id.toString(),
        displayName = "$id.jpg",
        previewBytes = null,
    )
}
