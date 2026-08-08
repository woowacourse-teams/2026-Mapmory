package com.mapmory.shared.presentation.map.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds

/**
 * A map-only viewport. The map can be panned or zoomed inside this region, but
 * its drawing is clipped before it reaches the surrounding app chrome.
 */
@Composable
fun MapViewport(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds(),
    ) {
        content()
    }
}
