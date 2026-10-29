package com.mapmory.shared.presentation.group

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalComposeUiApi::class)
class GroupRefreshGestureTest {
    @Test fun pullingAtTopRefreshes() = checkPull(initialScroll = 0, enabled = true, expected = 1)
    @Test fun scrollingTowardTopDoesNotRefreshBeforeReachingIt() = checkPull(initialScroll = 700, enabled = true, expected = 0)
    @Test fun disabledRefreshDoesNotStartRequests() = checkPull(initialScroll = 0, enabled = false, expected = 0)

    private fun checkPull(initialScroll: Int, enabled: Boolean, expected: Int) {
        var requests = 0
        val scroll = ScrollState(initialScroll)
        val scene = ImageComposeScene(412, 892) {
            GroupRefreshContainer(false, enabled, { requests++ }, Modifier.fillMaxSize()) {
                Column(Modifier.fillMaxSize().verticalScroll(scroll)) {
                    Spacer(Modifier.height(2200.dp))
                }
            }
        }
        var frame = 0L
        fun render() { repeat(30) { scene.render(++frame * 16_666_667L).close() } }
        try {
            render()
            scene.sendPointerEvent(PointerEventType.Press, Offset(200f, 180f), type = PointerType.Touch)
            for (step in 1..20) {
                scene.sendPointerEvent(PointerEventType.Move, Offset(200f, 180f + step * 20), type = PointerType.Touch)
                render()
            }
            scene.sendPointerEvent(PointerEventType.Release, Offset(200f, 580f), type = PointerType.Touch)
            render()
            assertEquals(expected, requests)
        } finally { scene.close() }
    }
}
