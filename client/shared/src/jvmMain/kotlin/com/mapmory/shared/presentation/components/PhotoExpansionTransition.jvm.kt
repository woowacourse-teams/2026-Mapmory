package com.mapmory.shared.presentation.components

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

internal actual fun photoExpansionTransition(isOpening: Boolean): ContentTransform =
    EnterTransition.None togetherWith ExitTransition.None

@Composable
internal actual fun photoViewerBackground(): Modifier = Modifier.background(Color.Black)
