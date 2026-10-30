package com.mapmory.shared.presentation.components

import androidx.compose.animation.ContentTransform
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

internal expect fun photoExpansionTransition(isOpening: Boolean): ContentTransform

@Composable
internal expect fun photoViewerBackground(): Modifier
