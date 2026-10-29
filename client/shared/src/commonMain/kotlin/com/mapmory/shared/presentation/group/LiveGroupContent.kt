package com.mapmory.shared.presentation.group

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.mapmory.shared.domain.repository.GroupRepository
import com.mapmory.shared.analytics.LocalMapmoryAnalytics
import com.mapmory.shared.navigation.MapmoryBackHandlerRegistry
import com.mapmory.shared.navigation.PlatformBackHandler
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LiveGroupContent(
    repository: GroupRepository,
    albumRepository: com.mapmory.shared.domain.repository.AlbumRepository?,
    shareCoordinator: AlbumShareCoordinator?,
    tripRecordRepository: com.mapmory.shared.domain.repository.TripRecordRepository,
    backHandlerRegistry: MapmoryBackHandlerRegistry,
    backHandlerOwnerId: String,
    refresh: Int,
    onBusyChanged: (Boolean) -> Unit,
    onPhotoVisibilityChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    leaveCoordinator: GroupLeaveCoordinator? = null,
    recordRevision: Long = 0,
    initialGroupId: Long? = null,
    initialAlbumId: Long? = null,
    onCreateRecord: (Long, Long?) -> Unit = { _, _ -> },
) {
    val analytics = LocalMapmoryAnalytics.current
    val state = remember(repository, analytics, leaveCoordinator) {
        LiveGroupState(repository, analytics) { leaveCoordinator?.membershipConfirmed(it) }
    }
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    var name by rememberSaveable { mutableStateOf("") }
    var code by rememberSaveable { mutableStateOf("") }
    val snackbar = remember { SnackbarHostState() }
    var noticeJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    fun clearNotice() {
        noticeJob?.cancel()
        snackbar.currentSnackbarData?.dismiss()
    }
    fun showNotice(message: String) {
        clearNotice()
        noticeJob = scope.launch { snackbar.showSnackbar(message) }
    }
    var refreshing by remember { mutableStateOf(false) }
    val leaveResult = leaveCoordinator?.state?.collectAsState()?.value
    val leaving = leaveResult?.status == GroupLeaveStatus.RUNNING
    var returnTargetRestored by remember(state) { mutableStateOf(false) }
    LaunchedEffect(state, refresh, recordRevision, initialGroupId) {
        state.load()
        if (!returnTargetRestored && initialGroupId != null) {
            state.openGroup(initialGroupId)
            returnTargetRestored = true
        }
    }
    LaunchedEffect(leaveResult) {
        if (leaveResult?.status == GroupLeaveStatus.LEFT) {
            state.removeGroup(leaveResult.groupId)
            if (leaveCoordinator.takeDepartureNotice() != null) {
                showNotice("그룹에서 나왔어요. 내 여행 기록은 그대로 남아 있어요.")
            }
        }
    }
    LaunchedEffect(state.busy) { onBusyChanged(state.busy) }
    DisposableEffect(Unit) { onDispose { onBusyChanged(false) } }
    val albumNavigation = remember(state.group?.id) { AlbumNavigationState() }
    var albumReturnRestored by remember(state) { mutableStateOf(false) }
    LaunchedEffect(state.group?.id, initialAlbumId) {
        if (!albumReturnRestored && state.group?.id == initialGroupId && initialAlbumId != null) {
            albumReturnRestored = true
            val revision = albumNavigation.revision
            albumRepository?.detail(checkNotNull(initialGroupId), initialAlbumId)?.onSuccess {
                if (albumNavigation.revision == revision) albumNavigation.showAlbum(it)
            }
        }
    }
    val back = rememberUpdatedState {
        if (leaving && state.sheet == LiveGroupSheet.LEAVE) true
        else if (state.sheet != null || state.busy) state.back()
        else albumNavigation.back() || state.back()
    }
    val gestureBack = rememberUpdatedState {
        if (leaving && state.sheet == LiveGroupSheet.LEAVE) true
        else if (state.sheet != null || state.busy) state.gestureBack()
        else albumNavigation.back() || state.gestureBack()
    }
    DisposableEffect(backHandlerRegistry, backHandlerOwnerId) {
        val registration = backHandlerRegistry.register(
            backHandlerOwnerId,
            gestureBack = { gestureBack.value() },
            gestureForward = state::gestureForward,
            handler = { back.value() },
        )
        onDispose { backHandlerRegistry.unregister(registration) }
    }
    PlatformBackHandler(enabled = state.busy || state.group != null || state.sheet != null) { back.value() }
    fun open(sheet: LiveGroupSheet) { clearNotice(); state.openSheet(sheet) }
    LaunchedEffect(state.error, state.sheet) {
        if (state.sheet == null && state.groups != null) state.error?.let {
            if (snackbar.showSnackbar(it, actionLabel = "다시 시도") == SnackbarResult.ActionPerformed) state.load()
        }
    }
    Box(modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize()) {
        val group = state.group
        Box(Modifier.weight(1f)) {
            if (group == null) {
                GroupRefreshContainer(refreshing, !state.busy, {
                    scope.launch {
                        refreshing = true
                        try { state.load() } finally { refreshing = false }
                    }
                }, Modifier.fillMaxSize()) {
                GroupListScreen(
                    state = when {
                        state.groups == null && state.busy -> GroupListUiState.Loading
                        state.groups == null && state.error != null -> GroupListUiState.Error(checkNotNull(state.error))
                        else -> GroupListUiState.Content(state.groups.orEmpty().map {
                            GroupSummary(it.id.toString(), it.name, it.memberCount.toInt(), albumCount = it.albumCount.toInt(), coverUri = it.coverPhoto?.viewUrl)
                        })
                    },
                    onCreate = { open(LiveGroupSheet.CREATE) },
                    onJoin = { open(LiveGroupSheet.JOIN) },
                    onGroup = { clearNotice(); scope.launch { state.openGroup(it.toLong()) } },
                    onRetry = { scope.launch { state.load() } },
                )
                }
            } else if (albumRepository != null && shareCoordinator != null) {
                key(group.id) {
                    LiveAlbumContent(group.id, albumRepository, tripRecordRepository, shareCoordinator, albumNavigation,
                        groupName = group.name, memberCount = group.members.size,
                        onBack = { back.value() }, onInvite = { open(LiveGroupSheet.INVITE) },
                        onManageGroup = { name = group.name; open(LiveGroupSheet.RENAME) },
                        onLeaveGroup = leaveCoordinator?.let { { open(LiveGroupSheet.LEAVE) } },
                        membershipBusy = leaving,
                        recordRevision = recordRevision,
                        onCreateRecord = { onCreateRecord(group.id, albumNavigation.detail.value?.id) },
                        onGroupUnavailable = { state.removeGroup(group.id) },
                        onPhotoVisibilityChanged = onPhotoVisibilityChanged)
                }
            }
        }
        if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (state.sheet != LiveGroupSheet.LEAVE && leaveResult != null && leaveResult.status != GroupLeaveStatus.LEFT) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
                Text(when (leaveResult.status) {
                    GroupLeaveStatus.RUNNING -> "그룹에서 나가고 있어요. 화면을 이동해도 계속 진행돼요."
                    GroupLeaveStatus.LEFT -> "그룹에서 나왔어요. 내 여행 기록은 그대로 남아 있어요."
                    GroupLeaveStatus.STILL_MEMBER -> "아직 그룹에 참여 중이에요. 그룹에서 다시 나갈 수 있어요."
                    GroupLeaveStatus.FAILED -> "그룹에서 나가지 못했어요. 참여 상태를 다시 확인해 주세요."
                    GroupLeaveStatus.UNKNOWN -> "그룹 나가기 결과를 확인하지 못했어요. 참여 상태를 확인해 주세요."
                })
                if (leaveResult.status in listOf(GroupLeaveStatus.UNKNOWN, GroupLeaveStatus.FAILED)) {
                    TextButton(onClick = { leaveCoordinator?.check(leaveResult.groupId) }) { Text("참여 상태 확인") }
                } else if (!leaving) TextButton(onClick = { leaveCoordinator?.dismiss() }) { Text("확인") }
            }
        }
    }
    SnackbarHost(snackbar, Modifier.align(androidx.compose.ui.Alignment.BottomCenter).padding(16.dp))
    }
    state.sheet?.let { sheet ->
        ModalBottomSheet(onDismissRequest = { if (!leaving) state.dismissSheet() }) {
            Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(when (sheet) {
                    LiveGroupSheet.CREATE -> "새 그룹 만들기"
                    LiveGroupSheet.RENAME -> "그룹 이름 변경"
                    LiveGroupSheet.JOIN -> "코드로 참여"
                    LiveGroupSheet.INVITE -> "멤버 초대"
                    LiveGroupSheet.LEAVE -> "그룹에서 나갈까요?"
                }, style = MaterialTheme.typography.headlineSmall)
                when (sheet) {
                    LiveGroupSheet.CREATE, LiveGroupSheet.RENAME -> {
                        OutlinedTextField(name, { name = it }, enabled = !state.busy && !(sheet == LiveGroupSheet.CREATE && state.creationUncertain),
                            label = { Text("그룹 이름") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        GroupMuted("앞뒤 공백을 제외하고 1~30자로 입력해 주세요.")
                        if (sheet == LiveGroupSheet.CREATE && state.creationUncertain) {
                            GroupButton("그룹 목록 확인", { scope.launch { state.reviewCreationResult() } }, Modifier.fillMaxWidth(), enabled = !state.busy)
                        } else GroupButton(if (sheet == LiveGroupSheet.CREATE) "그룹 만들기" else "이름 저장", {
                            clearNotice()
                            scope.launch {
                                if (sheet == LiveGroupSheet.CREATE) state.create(name) else state.rename(name)
                                if (state.sheet == null) name = ""
                            }
                        }, Modifier.fillMaxWidth(), enabled = name.isNotBlank() && !state.busy)
                    }
                    LiveGroupSheet.JOIN -> {
                        OutlinedTextField(code, { value ->
                            code = value.filter { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' }.take(6).uppercase()
                            state.resetPreview()
                        }, label = { Text("6자리 초대 코드") }, enabled = !state.busy, singleLine = true, modifier = Modifier.fillMaxWidth())
                        GroupButton("초대 확인", { scope.launch { state.checkInvitation(code) } }, Modifier.fillMaxWidth(), enabled = code.length == 6 && !state.busy)
                        state.preview?.let { preview ->
                            Text(preview.groupName, style = MaterialTheme.typography.titleMedium)
                            GroupMuted("멤버 ${preview.memberCount}명 / 최대 30명")
                            GroupButton(if (preview.alreadyMember) "그룹으로 이동" else "참여하기", {
                                scope.launch { state.acceptInvitation(code) }
                            }, Modifier.fillMaxWidth(), enabled = !state.busy && (!preview.full || preview.alreadyMember))
                        }
                    }
                    LiveGroupSheet.INVITE -> {
                        GroupMuted("멤버 누구나 초대할 수 있어요. 초대 코드는 발급 후 하루 동안 유효해요.")
                        val invitation = state.invitation
                        if (invitation == null) GroupButton("초대 코드 만들기", { scope.launch { state.issueInvitation() } }, Modifier.fillMaxWidth(), enabled = !state.busy)
                        else {
                            Text(invitation.code, style = MaterialTheme.typography.headlineLarge)
                            GroupMuted("만료: ${invitation.expiresAt.date} ${invitation.expiresAt.hour.toString().padStart(2, '0')}:${invitation.expiresAt.minute.toString().padStart(2, '0')}")
                            GroupButton("초대 코드 복사", {
                                clipboard.setText(AnnotatedString(invitation.code))
                                showNotice("초대 코드를 복사했어요")
                            }, Modifier.fillMaxWidth())
                        }
                    }
                    LiveGroupSheet.LEAVE -> {
                        GroupMuted("이 그룹의 앨범에서 내가 담은 기록과 사진이 내려가요. 내 여행 기록은 삭제되지 않아요.")
                        GroupMuted("다른 멤버의 기록이 없는 앨범은 삭제돼요. 마지막 멤버가 나가면 그룹과 초대 코드도 삭제돼요. 다시 참여하려면 새 초대가 필요해요.")
                        val currentResult = leaveResult?.takeIf { it.groupId == state.group?.id }
                        val unknown = currentResult?.status == GroupLeaveStatus.UNKNOWN
                        when (currentResult?.status) {
                            GroupLeaveStatus.FAILED -> Text("나가지 못했어요. 연결과 로그인 상태를 확인한 뒤 다시 시도해 주세요.")
                            GroupLeaveStatus.UNKNOWN -> Text("요청이 처리됐을 수 있어요. 나가기 전에 참여 상태부터 확인해 주세요.")
                            GroupLeaveStatus.STILL_MEMBER -> Text("참여 중인 상태를 확인했어요. 다시 나가기를 시도할 수 있어요.")
                            else -> Unit
                        }
                        val shareRunning = shareCoordinator?.operations?.collectAsState()?.value?.any { it.status == AlbumShareStatus.RUNNING } == true
                        if (shareRunning) Text("진행 중인 공유가 끝난 뒤 나갈 수 있어요.")
                        GroupButton(if (unknown) "참여 상태 확인" else "그룹 나가기", {
                            state.group?.id?.let { id ->
                                if (unknown) leaveCoordinator?.check(id) else leaveCoordinator?.leave(id)
                            }
                        }, Modifier.fillMaxWidth(), enabled = !leaving && !shareRunning && !state.busy)
                        if (leaving) LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                }
                if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = { state.dismissSheet() }, enabled = !state.busy && !leaving) { Text("닫기") }
                SnackbarHost(snackbar)
            }
        }
    }
}
