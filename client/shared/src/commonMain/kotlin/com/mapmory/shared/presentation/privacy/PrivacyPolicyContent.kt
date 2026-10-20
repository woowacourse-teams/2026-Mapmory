package com.mapmory.shared.presentation.privacy

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
expect fun PrivacyPolicyContent(
    url: String,
    modifier: Modifier = Modifier,
)
