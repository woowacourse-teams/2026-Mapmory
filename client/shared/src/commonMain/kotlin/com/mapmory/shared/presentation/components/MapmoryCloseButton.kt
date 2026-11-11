package com.mapmory.shared.presentation.components

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import mapmoryclient.shared.generated.resources.Res
import mapmoryclient.shared.generated.resources.close_outlined_24px
import org.jetbrains.compose.resources.painterResource

@Composable
internal fun MapmoryCloseButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    label: String = "닫기",
) {
    IconButton(onClick = onClick, modifier = modifier.size(48.dp)) {
        Icon(
            painter = painterResource(Res.drawable.close_outlined_24px),
            contentDescription = label,
            modifier = Modifier.size(24.dp),
        )
    }
}
