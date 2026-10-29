package com.mapmory.shared.presentation.group

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.flow.first
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mapmory.shared.app.AppContainer
import com.mapmory.shared.data.auth.MemberProvider
import com.mapmory.shared.navigation.MapmoryBackHandlerRegistry
import com.mapmory.shared.presentation.auth.SocialLoginPanel
import com.mapmory.shared.presentation.triprecord.screen.*

@Composable
internal fun GroupAccountRoute(
    container: AppContainer,
    onMapClick: () -> Unit,
    onRecordClick: () -> Unit,
    onProfileClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
    backHandlerRegistry: MapmoryBackHandlerRegistry,
    backHandlerOwnerId: String,
) {
    val session = container.accountSession
    val account = session?.state?.collectAsState()?.value
    var photoVisible by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var retry by remember { mutableStateOf(0) }
    var restoring by remember { mutableStateOf(true) }
    var restoreError by remember { mutableStateOf<String?>(null) }
    var noticeDismissed by remember { mutableStateOf(false) }
    LaunchedEffect(session, account?.generation, retry) {
        session?.state?.first { !it.transitioning }
        restoring = true; restoreError = null
        val result = session?.restore()
        val member = result?.getOrNull()
        restoring = false
        if (member == null) {
            if (account?.pendingLogin != true) restoreError = "계정 상태를 확인하지 못했어요. 다시 시도해 주세요."
            return@LaunchedEffect
        }
    }
    TripRecordBackground(modifier) {
        Column(Modifier.fillMaxSize()) {
            val needsLogin = account?.pendingLogin == true || account?.recoveryRequired == true ||
                account?.member?.provider == MemberProvider.GUEST
            when {
                restoring -> Box(Modifier.weight(1f).fillMaxWidth().padding(24.dp)) { CircularProgressIndicator() }
                restoreError != null || needsLogin -> Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp)) {
                    Text("그룹", style = MaterialTheme.typography.headlineMedium)
                    restoreError?.let { Text(it) }
                    SocialLoginPanel(session, container.accountLoginController)
                    TextButton(onClick = { retry++ }) { Text("계정 확인 다시 시도") }
                }
                container.groupRepository != null -> key(account?.generation) {
                    LiveGroupContent(
                        repository = checkNotNull(container.groupRepository),
                        albumRepository = container.albumRepository,
                        tripRecordRepository = container.tripRecordRepository,
                        backHandlerRegistry = backHandlerRegistry,
                        backHandlerOwnerId = backHandlerOwnerId,
                        refresh = retry,
                        onBusyChanged = { busy = it },
                        onPhotoVisibilityChanged = { photoVisible = it },
                        modifier = Modifier.weight(1f),
                    )
                }
                else -> Box(Modifier.weight(1f).padding(24.dp)) { Text("그룹 서버 연결을 사용할 수 없어요.") }
            }
            if (!photoVisible) TripBottomBar(selected = TripBottomTab.GROUP, onMapClick = onMapClick,
                onRecordClick = onRecordClick, onProfileClick = onProfileClick,
                onSettingsClick = onSettingsClick, onGroupClick = { retry++ })
        }
        if (account?.guestAbandoned == true && !noticeDismissed) AlertDialog(
            onDismissRequest = { noticeDismissed = true }, title = { Text("기존 계정으로 로그인했어요") },
            text = { Text("이 계정의 기록으로 전환했어요. 게스트로 남긴 기록은 자동으로 합쳐지지 않아요.") },
            confirmButton = { TextButton(onClick = { noticeDismissed = true }) { Text("확인") } },
        )
    }
}
