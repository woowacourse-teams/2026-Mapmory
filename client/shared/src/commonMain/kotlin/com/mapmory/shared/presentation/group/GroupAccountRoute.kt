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
    initialGroupId: Long? = null,
    initialAlbumId: Long? = null,
    notificationLoginMessage: String? = null,
    onCreateRecord: (Long, Long?) -> Unit = { _, _ -> },
    backHandlerRegistry: MapmoryBackHandlerRegistry,
    backHandlerOwnerId: String,
) {
    val session = container.accountSession
    val account = session?.state?.collectAsState()?.value
    val recordRevision by container.tripRecordRevision.collectAsState()
    var photoVisible by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var retry by remember { mutableStateOf(0) }
    var restoring by remember { mutableStateOf(true) }
    var restoreError by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(session, account?.generation, retry) {
        session?.state?.first { !it.transitioning }
        restoring = true; restoreError = null
        val result = session?.restore()
        val member = result?.getOrNull()
        restoring = false
        if (member == null) {
            if (account?.pendingLogin != true) restoreError = "계정 정보를 불러오지 못했어요. 연결 상태를 확인한 뒤 화면을 다시 열어 주세요."
            return@LaunchedEffect
        }
    }
    TripRecordBackground(modifier) {
        GroupTheme {
        Column(Modifier.fillMaxSize()) {
            val needsLogin = account?.pendingLogin == true || account?.member?.provider == MemberProvider.GUEST
            when {
                restoring -> Box(Modifier.weight(1f).fillMaxWidth().padding(24.dp), contentAlignment = androidx.compose.ui.Alignment.Center) { CircularProgressIndicator() }
                account?.recoveryRequired == true -> Column(Modifier.weight(1f).fillMaxWidth().padding(20.dp)) {
                    Text("그룹 연결을 복구해 주세요", style = MaterialTheme.typography.headlineMedium)
                    Text("현재 계정을 다시 인증하면 그룹을 계속 이용할 수 있어요.")
                    TextButton(
                        onClick = { container.accountLoginController?.requestRecoveryPrompt() },
                        enabled = container.accountLoginController != null,
                    ) { Text("계정 복구") }
                }
                restoreError != null || needsLogin -> Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp)) {
                    Text("그룹", style = MaterialTheme.typography.headlineMedium)
                    notificationLoginMessage?.let { Text(it, modifier = Modifier.padding(top = 8.dp, bottom = 12.dp)) }
                    restoreError?.let { Text(it) }
                    SocialLoginPanel(session, container.accountLoginController)
                }
                container.groupRepository != null -> key(account?.generation) {
                    LiveGroupContent(
                        repository = checkNotNull(container.groupRepository),
                        albumRepository = container.albumRepository,
                        shareCoordinator = container.albumShareCoordinator,
                        leaveCoordinator = container.groupLeaveCoordinator,
                        recordRevision = recordRevision,
                        initialGroupId = initialGroupId,
                        initialAlbumId = initialAlbumId,
                        onCreateRecord = onCreateRecord,
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

        }
    }
}
