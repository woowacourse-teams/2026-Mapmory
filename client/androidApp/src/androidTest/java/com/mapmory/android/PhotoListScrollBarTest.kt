package com.mapmory.android

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.mapmory.shared.presentation.components.PhotoListScrollBar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class PhotoListScrollBarTest {
    @get:Rule val composeRule = createComposeRule()
    private lateinit var state: LazyListState

    private fun showList() {
        composeRule.setContent {
            state = rememberLazyListState()
            Box(Modifier.fillMaxSize()) {
                LazyColumn(
                    state = state,
                    contentPadding = PaddingValues(top = 24.dp, bottom = 36.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(300) { index ->
                        Text("사진 $index", Modifier.fillMaxWidth().height((48 + index % 5 * 16).dp))
                    }
                }
                PhotoListScrollBar(state, Modifier.align(Alignment.CenterEnd))
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun paddedListSeeksToActualHalfwayAndBothEnds() {
        composeRule.setContent {
            state = rememberLazyListState()
            Box(Modifier.fillMaxSize()) {
                LazyColumn(
                    state = state,
                    contentPadding = PaddingValues(top = 96.dp, bottom = 36.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(100) { index ->
                        Text("사진 $index", Modifier.fillMaxWidth().height(64.dp))
                    }
                }
                PhotoListScrollBar(state, Modifier.align(Alignment.CenterEnd))
            }
        }
        composeRule.waitForIdle()
        val bar = composeRule.onNodeWithTag("photo-list-scrollbar")
        bar.performTouchInput {
            down(center)
            up()
        }
        composeRule.runOnIdle {
            val layout = state.layoutInfo
            val itemSize = layout.visibleItemsInfo.first().size
            val stride = itemSize + layout.mainAxisItemSpacing
            val viewport = layout.viewportEndOffset - layout.viewportStartOffset
            val content = layout.beforeContentPadding + layout.afterContentPadding +
                itemSize * 100 + layout.mainAxisItemSpacing * 99
            val actual = state.firstVisibleItemIndex * stride + state.firstVisibleItemScrollOffset
            assertEquals((content - viewport) / 2f, actual.toFloat(), 2f)
        }
        bar.performTouchInput { down(Offset(centerX, height - 1f)); up() }
        composeRule.runOnIdle { assertTrue(!state.canScrollForward) }
        bar.performTouchInput { down(Offset(centerX, 0f)); up() }
        composeRule.runOnIdle {
            assertTrue(!state.canScrollBackward)
            assertEquals(0, state.firstVisibleItemIndex)
            assertEquals(0, state.firstVisibleItemScrollOffset)
        }
    }

    @Test
    fun thumbDragSurvivesRemeasureAndCanReverseWithoutLiftingFinger() {
        showList()
        val bar = composeRule.onNodeWithTag("photo-list-scrollbar")
        bar.performTouchInput {
            down(Offset(centerX, 8f))
            moveTo(Offset(centerX, height * 0.3f), delayMillis = 120)
        }
        composeRule.waitForIdle()
        var firstIndex = 0
        composeRule.runOnIdle {
            firstIndex = state.firstVisibleItemIndex
            assertTrue(firstIndex > 0)
        }
        bar.performTouchInput { moveTo(Offset(centerX, height * 0.8f), delayMillis = 120) }
        composeRule.waitForIdle()
        composeRule.runOnIdle { assertTrue(state.firstVisibleItemIndex > firstIndex) }
        bar.performTouchInput { moveTo(Offset(centerX, 8f), delayMillis = 120) }
        composeRule.waitForIdle()
        composeRule.runOnIdle { assertEquals(0, state.firstVisibleItemIndex) }
        bar.performTouchInput { up() }
    }

    @Test
    fun tappingTrackStillSeeksWithoutDragging() {
        showList()
        composeRule.onNodeWithTag("photo-list-scrollbar").performTouchInput {
            down(Offset(centerX, height * 0.7f))
            up()
        }
        composeRule.waitForIdle()
        composeRule.runOnIdle { assertTrue(state.firstVisibleItemIndex > 100) }
    }
}
