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
    var restoring by remember { mutableStateOf(account?.member == null) }
    var restoreError by remember { mutableStateOf<String?>(null) }
    val invitationInbox = LocalGroupInvitationInbox.current
    val invitationUrl = invitationInbox?.pendingUrl
    var invitationPreview by remember(invitationUrl) { mutableStateOf<com.mapmory.shared.domain.group.GroupInvitationPreview?>(null) }
    var invitationError by remember(invitationUrl) { mutableStateOf<String?>(null) }
    var invitationLoading by remember(invitationUrl) { mutableStateOf(false) }
    var invitationCode by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
    LaunchedEffect(invitationUrl, restoring, account?.generation, retry) {
        val token = invitationUrl?.let(::groupInvitationToken) ?: return@LaunchedEffect
        if (restoring || restoreError != null || account?.transitioning == true ||
            account?.member?.provider != MemberProvider.GUEST || account.pendingLogin || account.recoveryRequired
        ) return@LaunchedEffect
        val repository = container.groupRepository ?: return@LaunchedEffect
        invitationLoading = true
        invitationError = null
        try {
            repository.previewInvitation(token).onSuccess { invitationPreview = it }.onFailure {
                invitationError = if ((it as? com.mapmory.shared.data.remote.MapmoryApiException)?.code == "INVITATION_NOT_FOUND")
                    "초대가 없거나 만료됐어요. 새 초대를 요청해 주세요."
                else "초대 정보를 불러오지 못했어요. 다시 시도해 주세요."
            }
        } finally { invitationLoading = false }
    }
    LaunchedEffect(session, account?.generation, retry) {
        session?.state?.first { !it.transitioning }
        restoring = account?.member == null; restoreError = null
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
                restoring -> GroupListScreen(
                    state = GroupListUiState.Loading,
                    onCreate = {}, onJoin = {}, onGroup = {}, onRetry = {},
                    modifier = Modifier.weight(1f),
                )
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
                    invitationInbox?.pendingUrl?.let { url ->
                        invitationPreview?.let { preview ->
                            Text("${preview.groupName} 그룹에 초대됐어요.", style = MaterialTheme.typography.titleMedium)
                            Text("멤버 ${preview.memberCount}명 / 최대 30명")
                        }
                        if (invitationLoading) CircularProgressIndicator()
                        invitationError?.let { message ->
                            Text(message)
                            TextButton(onClick = { retry++ }) { Text("초대 다시 확인") }
                        }
                        Text("로그인 후 이 초대에 참여할 수 있어요.")
                        TextButton(onClick = { invitationInbox.dismiss(url) }) { Text("초대 닫기") }
                    }
                    if (invitationInbox != null && invitationUrl == null) {
                        OutlinedTextField(
                            value = invitationCode,
                            onValueChange = { value -> invitationCode = value.filter { it.isLetterOrDigit() && it.code < 128 }.take(6).uppercase() },
                            label = { Text("6자리 초대 코드") },
                            supportingText = { Text("받은 코드를 입력하거나 길게 눌러 붙여넣어 주세요.") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        TextButton(onClick = {
                            invitationInbox.receive("https://map-mory.com/invite/$invitationCode")
                            invitationCode = ""
                        }, enabled = invitationCode.length == 6) { Text("초대 확인") }
                    }
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
                        cacheScope = account?.generation ?: 0,
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
