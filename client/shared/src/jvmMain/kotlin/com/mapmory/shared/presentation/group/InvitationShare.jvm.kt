package com.mapmory.shared.presentation.group

import androidx.compose.runtime.Composable
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

@Composable
internal actual fun rememberInvitationShare(): (String) -> Unit = { url ->
    Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(url), null)
}
