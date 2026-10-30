package com.mapmory.shared.presentation.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.mapmory.shared.presentation.triprecord.screen.TripRecordPalette
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlin.math.roundToInt

internal data class PhotoScrollBarMetrics(val thumbFraction: Float, val scrollFraction: Float)
internal data class PhotoScrollTarget(val itemIndex: Int, val itemScrollOffset: Int)

internal fun photoScrollBarMetrics(scrollOffset: Int, viewportSize: Int, contentSize: Int): PhotoScrollBarMetrics? {
    if (viewportSize <= 0 || contentSize <= viewportSize) return null
    return PhotoScrollBarMetrics(
        thumbFraction = viewportSize.toFloat() / contentSize,
        scrollFraction = (scrollOffset.toFloat() / (contentSize - viewportSize)).coerceIn(0f, 1f),
    )
}

internal fun photoScrollTarget(
    scrollFraction: Float,
    viewportSize: Int,
    itemSizes: List<Int>,
    itemSpacing: Int,
    contentPaddingBefore: Int,
    contentPaddingAfter: Int,
): PhotoScrollTarget? {
    if (viewportSize <= 0 || itemSizes.isEmpty()) return null
    val contentSize = contentPaddingBefore + contentPaddingAfter + itemSizes.sum() +
        itemSpacing * (itemSizes.size - 1).coerceAtLeast(0)
    // scrollToItem uses the same origin as firstVisibleItemScrollOffset: item 0 at offset 0
    // already leaves beforeContentPadding visible. Subtracting it here would under-scroll.
    var offset = (scrollFraction.coerceIn(0f, 1f) * (contentSize - viewportSize).coerceAtLeast(0)).roundToInt()
    itemSizes.forEachIndexed { index, itemSize ->
        if (offset < itemSize + itemSpacing || index == itemSizes.lastIndex) {
            return PhotoScrollTarget(index, offset.coerceIn(0, itemSize))
        }
        offset -= itemSize + itemSpacing
    }
    return null
}

/** Preserve where the finger grabbed the thumb; map thumb travel, rather than track height. */
internal fun photoScrollFraction(pointerY: Float, grabOffset: Float, trackHeight: Float, thumbHeight: Float): Float =
    if (trackHeight <= thumbHeight) 0f
    else ((pointerY - grabOffset) / (trackHeight - thumbHeight)).coerceIn(0f, 1f)

private data class PhotoScrollLayout(
    val viewportSize: Int,
    val itemSizes: List<Int>,
    val itemSpacing: Int,
    val paddingBefore: Int,
    val paddingAfter: Int,
    val metrics: PhotoScrollBarMetrics,
)

/** Shared Android/iOS fast scrubber. Only touches starting on the scrollbar claim the gesture. */
@Composable
fun PhotoListScrollBar(
    listState: LazyListState,
    modifier: Modifier = Modifier,
    contentKey: Any? = null,
    testTag: String = "photo-list-scrollbar",
) {
    val layout = listState.layoutInfo
    if (layout.visibleItemsInfo.isEmpty() || (!listState.canScrollBackward && !listState.canScrollForward)) return
    // Reset estimates after filtering, changing columns, or resizing the viewport.
    val knownSizes = remember(listState, contentKey, layout.viewportSize.width, layout.totalItemsCount) {
        mutableMapOf<Int, Int>()
    }
    layout.visibleItemsInfo.forEach { knownSizes[it.index] = it.size }
    val averageSize = knownSizes.values.average().roundToInt().coerceAtLeast(1)
    val sizes = List(layout.totalItemsCount) { knownSizes[it] ?: averageSize }
    val spacing = layout.mainAxisItemSpacing
    val contentSize = layout.beforeContentPadding + layout.afterContentPadding + sizes.sum() +
        spacing * (sizes.size - 1).coerceAtLeast(0)
    val viewportSize = layout.viewportEndOffset - layout.viewportStartOffset
    val offset = when {
        !listState.canScrollBackward -> 0
        !listState.canScrollForward -> contentSize - viewportSize
        else -> sizes.take(listState.firstVisibleItemIndex).sum() +
            spacing * listState.firstVisibleItemIndex + listState.firstVisibleItemScrollOffset
    }
    val metrics = photoScrollBarMetrics(offset, viewportSize, contentSize) ?: return
    val latestLayout by rememberUpdatedState(
        PhotoScrollLayout(viewportSize, sizes, spacing, layout.beforeContentPadding, layout.afterContentPadding, metrics),
    )
    val requests = remember(listState) { Channel<Float>(Channel.CONFLATED) }
    var dragFraction by remember(listState) { mutableStateOf<Float?>(null) }
    LaunchedEffect(listState) {
        requests.receiveAsFlow().collectLatest { fraction ->
            val current = latestLayout
            val target = photoScrollTarget(
                fraction, current.viewportSize, current.itemSizes, current.itemSpacing,
                current.paddingBefore, current.paddingAfter,
            ) ?: return@collectLatest
            listState.scrollToItem(target.itemIndex, target.itemScrollOffset)
        }
    }
    val trackColor = TripRecordPalette.current.line.copy(alpha = 0.65f)
    val thumbColor = TripRecordPalette.current.accent.copy(alpha = 0.9f)
    Canvas(
        modifier = modifier
            .width(24.dp)
            .fillMaxHeight()
            .testTag(testTag)
            .semantics { contentDescription = "사진 목록 스크롤바" }
            // Estimates change on every scroll: keying the detector by them cancels an active drag.
            .pointerInput(listState) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    down.consume()
                    val current = latestLayout.metrics
                    val trackHeight = size.height.toFloat()
                    val thumbHeight = (trackHeight * current.thumbFraction).coerceAtLeast(44.dp.toPx()).coerceAtMost(trackHeight)
                    val thumbTop = (trackHeight - thumbHeight) * current.scrollFraction
                    val grabOffset = if (down.position.y in thumbTop..(thumbTop + thumbHeight)) {
                        down.position.y - thumbTop
                    } else thumbHeight / 2f
                    fun seek(y: Float) {
                        val fraction = photoScrollFraction(y, grabOffset, trackHeight, thumbHeight)
                        dragFraction = fraction
                        requests.trySend(fraction)
                    }
                    try {
                        seek(down.position.y)
                        do {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (change.pressed) seek(change.position.y)
                            event.changes.forEach { it.consume() }
                        } while (change.pressed)
                    } finally {
                        dragFraction = null
                    }
                }
            },
    ) {
        val thumbHeight = (size.height * metrics.thumbFraction).coerceAtLeast(44.dp.toPx()).coerceAtMost(size.height)
        val thumbTop = (size.height - thumbHeight) * (dragFraction ?: metrics.scrollFraction)
        val barWidth = 4.dp.toPx()
        val x = (size.width - barWidth) / 2f
        val radius = CornerRadius(barWidth / 2f)
        drawRoundRect(trackColor, Offset(x, 0f), Size(barWidth, size.height), radius)
        drawRoundRect(thumbColor, Offset(x, thumbTop), Size(barWidth, thumbHeight), radius)
    }
}
