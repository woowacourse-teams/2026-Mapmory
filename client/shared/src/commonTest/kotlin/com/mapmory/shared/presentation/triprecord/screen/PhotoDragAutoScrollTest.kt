package com.mapmory.shared.presentation.triprecord.screen

import com.mapmory.shared.presentation.components.photoScrollBarMetrics
import com.mapmory.shared.presentation.components.photoScrollTarget
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.mapmory.shared.presentation.photo.PhotoRecommendationPagingState
import com.mapmory.shared.presentation.photo.SelectedPhoto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PhotoDragAutoScrollTest {
    @Test
    fun `최대 선택 수까지 모두 선택하면 버튼은 모두 해제로 전환된다`() {
        val photos = (0 until 120).map { index ->
            SelectedPhoto("photo-$index", "photo-$index.jpg", previewBytes = null)
        }
        val state = PhotoRecommendationPagingState(
            photos = photos,
            maxSelectionCount = 100,
        ).toggleAllPhotos()

        assertEquals(100, state.selectedIds.size)
        assertTrue(state.isAllSelectionActive())
        assertEquals(emptySet(), state.toggleAllPhotos().selectedIds)
    }

    @Test
    fun `가운데에서는 자동 스크롤하지 않는다`() {
        assertEquals(
            0f,
            photoDragAutoScrollVelocity(
                pointerY = 500f,
                viewportTop = 100f,
                viewportBottom = 900f,
                edgeSize = 100f,
                maximumStep = 30f,
            ),
        )
    }

    @Test
    fun `하단 끝에 가까울수록 아래 스크롤 속도가 빨라진다`() {
        val nearEdge = photoDragAutoScrollVelocity(820f, 100f, 900f, 100f, 30f)
        val atEdge = photoDragAutoScrollVelocity(900f, 100f, 900f, 100f, 30f)

        assertTrue(nearEdge > 0f)
        assertTrue(atEdge > nearEdge)
        assertEquals(30f, atEdge)
    }

    @Test
    fun `상단 끝에서는 위 방향 최대 속도를 반환한다`() {
        assertEquals(
            -30f,
            photoDragAutoScrollVelocity(100f, 100f, 900f, 100f, 30f),
        )
    }

    @Test
    fun `사진 목록 스크롤바는 보이는 비율과 이동 위치를 계산한다`() {
        val metrics = photoScrollBarMetrics(
            scrollOffset = 650,
            viewportSize = 400,
            contentSize = 2_000,
        )

        requireNotNull(metrics)
        assertEquals(0.2f, metrics.thumbFraction)
        assertEquals(0.40625f, metrics.scrollFraction)
    }

    @Test
    fun `전체 항목이 한 화면에 보이면 스크롤바를 표시하지 않는다`() {
        assertNull(
            photoScrollBarMetrics(
                scrollOffset = 0,
                viewportSize = 500,
                contentSize = 500,
            ),
        )
    }

    @Test
    fun `드래그가_지나간_사진은_포인터가_다른_열로_이동해도_선택을_유지한다`() {
        val selectedIds = mutableSetOf<String>()
        val controller = PhotoDragSelectionController(
            selectedIds = { selectedIds.toSet() },
            onSelectionChanged = { photoId, selected ->
                if (selected) selectedIds += photoId else selectedIds -= photoId
            },
        )
        controller.updateBounds("top-left", Rect(0f, 0f, 10f, 10f))
        controller.updateBounds("top-right", Rect(20f, 0f, 30f, 10f))
        controller.updateBounds("bottom-left", Rect(0f, 20f, 10f, 30f))

        controller.start("top-left", Offset(5f, 5f))
        controller.moveTo(Offset(25f, 5f))
        controller.moveTo(Offset(5f, 25f))

        assertEquals(setOf("top-left", "top-right", "bottom-left"), selectedIds)
        controller.moveTo(Offset(25f, 5f))
        assertEquals(setOf("top-left", "top-right"), selectedIds)
        controller.moveTo(Offset(5f, 5f))
        assertEquals(setOf("top-left"), selectedIds)
    }

    @Test
    fun `스크롤로_시작사진이_사라져도_범위를_확장하고_복원한다`() {
        var order = listOf("a", "b", "c", "d")
        val selection = mutableSetOf("c")
        val controller = PhotoDragSelectionController(
            selectedIds = { selection },
            orderedIds = { order },
            onSelectionChanged = { id, selected ->
                if (selected) selection.add(id) else selection.remove(id)
            },
        )
        controller.updateBounds("a", Rect(0f, 0f, 10f, 10f))
        controller.start("a", Offset(5f, 5f))
        controller.remove("a")
        order = order + "e"
        controller.updateBounds("e", Rect(0f, 20f, 10f, 30f))
        controller.moveTo(Offset(5f, 25f))
        assertEquals(order.toSet(), selection)
        controller.updateBounds("b", Rect(0f, 0f, 10f, 10f))
        controller.moveTo(Offset(5f, 5f))
        assertEquals(setOf("a", "b", "c"), selection)
    }

    @Test
    fun `선택된_사진에서_시작한_해제_드래그도_되돌리면_복원한다`() {
        val selection = mutableSetOf("a", "b", "c")
        val controller = PhotoDragSelectionController(
            selectedIds = { selection },
            orderedIds = { listOf("a", "b", "c") },
            onSelectionChanged = { id, selected ->
                if (selected) selection.add(id) else selection.remove(id)
            },
        )
        controller.updateBounds("a", Rect(0f, 0f, 10f, 10f))
        controller.updateBounds("c", Rect(0f, 20f, 10f, 30f))
        controller.start("a", Offset(5f, 5f))
        controller.moveTo(Offset(5f, 25f))
        assertTrue(selection.isEmpty())
        controller.moveTo(Offset(5f, 5f))
        assertEquals(setOf("b", "c"), selection)
    }

    @Test
    fun `상한에서_시작점_반대편으로_드래그해도_복원_후_새범위를_선택한다`() {
        val selection = mutableSetOf("existing")
        val controller = PhotoDragSelectionController(
            selectedIds = { selection },
            orderedIds = { listOf("a", "b", "c", "d") },
            maxSelectionCount = 3,
            onSelectionChanged = { id, selected ->
                if (selected) selection.add(id) else selection.remove(id)
                assertTrue(selection.size <= 3)
            },
        )
        listOf("a", "b", "c", "d").forEachIndexed { index, id ->
            controller.updateBounds(id, Rect(0f, index * 20f, 10f, index * 20f + 10f))
        }
        controller.start("b", Offset(5f, 25f))
        controller.moveTo(Offset(5f, 65f))
        assertEquals(setOf("existing", "b", "c"), selection)
        controller.moveTo(Offset(5f, 5f))
        assertEquals(setOf("existing", "a", "b"), selection)
    }

    @Test
    fun `스크롤바_끝을_드래그하면_마지막_항목_내부_위치를_계산한다`() {
        val target = photoScrollTarget(
            scrollFraction = 1f,
            viewportSize = 200,
            itemSizes = listOf(100, 200, 300),
            itemSpacing = 10,
            contentPaddingBefore = 20,
            contentPaddingAfter = 30,
        )

        requireNotNull(target)
        assertEquals(2, target.itemIndex)
        assertEquals(150, target.itemScrollOffset)
    }
}
