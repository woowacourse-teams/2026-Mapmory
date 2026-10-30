package com.mapmory.shared.presentation.components

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color

internal actual fun photoExpansionTransition(isOpening: Boolean): ContentTransform =
    if (isOpening) {
        // Keep the grid visible beneath the gradually darkening backdrop.
        // The shared photo itself stays fully opaque throughout its expansion.
        EnterTransition.None togetherWith fadeOut(tween(durationMillis = 260), targetAlpha = 1f)
    } else {
        // Reveal the grid throughout the shared photo's motion, without a delayed second step.
        fadeIn(tween(durationMillis = 200)) togetherWith
            fadeOut(tween(durationMillis = 160))
    }

@Composable
internal actual fun photoViewerBackground(): Modifier {
    val opacity = remember { Animatable(0f) }
    LaunchedEffect(Unit) { opacity.animateTo(1f, tween(durationMillis = 260)) }
    return Modifier.drawBehind { drawRect(Color.Black, alpha = opacity.value) }
}
