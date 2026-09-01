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
            PhotoRecommendationPage(1, (49..50).map { photo(it) }, hasMore = false),
        )

        val first = PhotoRecommendationPagingState().accept(pages[0])
        val second = first?.accept(pages[1])
        val third = second?.accept(pages[2])

        assertNotNull(first)
        assertNotNull(second)
        assertNotNull(third)
        assertEquals(24, first.photos.size)
        assertEquals(48, second.photos.size)
        assertEquals(50, third.photos.size)
        assertEquals(2, third.pageIndex)
        assertFalse(third.hasMore)
        assertEquals(50, third.photos.map(SelectedPhoto::id).toSet().size)
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
