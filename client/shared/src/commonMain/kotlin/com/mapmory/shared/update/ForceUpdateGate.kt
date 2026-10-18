package com.mapmory.shared.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties

/** Only a confirmed update restriction covers the splash or current navigation state. */
@Composable
internal fun ForceUpdateGate(state: ForceUpdateState, onRetry: () -> Unit) {
    if (state.isChecking) return

    val uriHandler = LocalUriHandler.current
    var storeError by remember { mutableStateOf(false) }
    if (state.isUpdateRequired) {
        AlertDialog(
            onDismissRequest = {},
            properties = DialogProperties(
                dismissOnBackPress = false,
                dismissOnClickOutside = false,
            ),
            title = { Text("업데이트가 필요해요") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("맵모리를 계속 이용하려면 최신 버전으로 업데이트해 주세요.")
                    Text("현재 ${state.currentVersion} · 최소 ${state.minimumVersion}")
                    if (storeError) Text("스토어를 열지 못했어요. 다시 시도해 주세요.")
                }
            },
            confirmButton = {
                Button(onClick = {
                    storeError = runCatching { uriHandler.openUri(state.storeUrl) }.isFailure
                }) { Text("업데이트") }
            },
            dismissButton = {
                TextButton(onClick = onRetry) { Text("다시 확인") }
            },
        )
    } else if (state.isStoreUpdateAvailable) {
        AlertDialog(
            onDismissRequest = state::dismissStoreUpdate,
            title = { Text("새 버전이 있어요") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("최신 버전으로 업데이트하고 새로운 기능을 만나보세요.")
                    if (storeError) Text("스토어를 열지 못했어요. 다시 시도해 주세요.")
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    storeError = runCatching { uriHandler.openUri(state.storeUrl) }.isFailure
                }) { Text("업데이트") }
            },
            dismissButton = {
                TextButton(onClick = state::dismissStoreUpdate) { Text("나중에") }
            },
        )
    }
}
