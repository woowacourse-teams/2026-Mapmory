package com.mapmory.shared.presentation.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import kotlin.math.abs

/** Claims only a downward, single-finger drag; horizontal paging and pinch keep priority. */
@Composable
internal fun Modifier.photoDragDown(
    canStart: () -> Boolean,
    onStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onFinish: (cancelled: Boolean) -> Unit,
): Modifier {
    val allowed = rememberUpdatedState(canStart)
    val start = rememberUpdatedState(onStart)
    val drag = rememberUpdatedState(onDrag)
    val finish = rememberUpdatedState(onFinish)
    return pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            if (!allowed.value()) return@awaitEachGesture
            var dragging = false
            var finished = false
            try {
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (event.changes.any { it.id != down.id && it.pressed } ||
                        change.isConsumed || !allowed.value()
                    ) break
                    if (!change.pressed) {
                        if (dragging) {
                            finished = true
                            finish.value(false)
                        }
                        break
                    }
                    if (!dragging) {
                        val distance = change.position - down.position
                        if ((abs(distance.x) > viewConfiguration.touchSlop &&
                                abs(distance.x) >= abs(distance.y)) ||
                            distance.y < -viewConfiguration.touchSlop
                        ) break
                        if (distance.y <= viewConfiguration.touchSlop) continue
                        dragging = true
                        start.value()
                        drag.value(distance.y - viewConfiguration.touchSlop)
                    } else {
                        drag.value(change.positionChange().y)
                    }
                    change.consume()
                }
            } finally {
                if (dragging && !finished) finish.value(true)
            }
        }
    }
}
