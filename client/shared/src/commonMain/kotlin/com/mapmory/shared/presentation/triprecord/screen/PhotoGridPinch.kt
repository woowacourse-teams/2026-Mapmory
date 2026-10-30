package com.mapmory.shared.presentation.triprecord.screen

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput

/** Claim multi-touch before scrolling or card gestures, until every finger is lifted. */
internal fun Modifier.photoGridPinch(
    columnCount: Int,
    onColumnCountChange: (Int) -> Unit,
    onPinchingChange: (Boolean) -> Unit,
): Modifier = composed {
    val currentColumns by rememberUpdatedState(columnCount)
    val changeColumns by rememberUpdatedState(onColumnCountChange)
    val changePinching by rememberUpdatedState(onPinchingChange)
    pointerInput(Unit) {
        try {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                var claimed = false
                var zoom = 1f
                var columns = currentColumns
                do {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    if (event.changes.count { it.pressed } >= 2) {
                        if (!claimed) {
                            claimed = true
                            changePinching(true)
                        }
                        zoom *= event.calculateZoom()
                        photoGridPinchResultAfterZoom(columns, zoom)?.let {
                            columns = it.columnCount
                            zoom = it.accumulatedZoom
                            changeColumns(columns)
                        }
                    }
                    if (claimed) event.changes.forEach { it.consume() }
                } while (event.changes.any { it.pressed })
                if (claimed) changePinching(false)
            }
        } finally {
            changePinching(false)
        }
    }
}
