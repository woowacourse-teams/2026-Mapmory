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
    backHandlerRegistry: MapmoryBackHandlerRegistry,
    backHandlerOwnerId: String,
    refresh: Int,
    onBusyChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val analytics = LocalMapmoryAnalytics.current
    val state = remember(repository, analytics) { LiveGroupState(repository, analytics) }
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    var name by rememberSaveable { mutableStateOf("") }
    var code by rememberSaveable { mutableStateOf("") }
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(state, refresh) { state.load() }
    LaunchedEffect(state.busy) { onBusyChanged(state.busy) }
    DisposableEffect(Unit) { onDispose { onBusyChanged(false) } }
    val back = rememberUpdatedState { state.back() }
    DisposableEffect(backHandlerRegistry, backHandlerOwnerId) {
        val registration = backHandlerRegistry.register(
            backHandlerOwnerId,
            gestureBack = state::gestureBack,
            gestureForward = state::gestureForward,
            handler = { back.value() },
        )
        onDispose { backHandlerRegistry.unregister(registration) }
    }
    PlatformBackHandler(enabled = state.busy || state.group != null || state.sheet != null) { state.back() }
    fun open(sheet: LiveGroupSheet) { copied = false; state.openSheet(sheet) }
    Column(modifier.fillMaxSize()) {
        val group = state.group
        if (group == null) {
            Text("내 그룹", Modifier.padding(24.dp), style = MaterialTheme.typography.headlineMedium)
            Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                GroupButton("새 그룹 만들기", { open(LiveGroupSheet.CREATE) }, Modifier.weight(1f), enabled = !state.busy)
                GroupButton("코드로 참여", { open(LiveGroupSheet.JOIN) }, Modifier.weight(1f), outlined = true, enabled = !state.busy)
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (state.groups?.isEmpty() == true) {
                    Text("아직 참여한 그룹이 없어요.")
                    GroupMuted("그룹을 만들거나 초대 코드로 참여해 보세요.")
                }
                state.groups.orEmpty().forEach { summary ->
                    GroupCard(onClick = { scope.launch { state.openGroup(summary.id) } }) {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(summary.name, style = MaterialTheme.typography.titleMedium)
                            GroupMuted("멤버 ${summary.memberCount}명 · 앨범 ${summary.albumCount}개")
                        }
                    }
                }
                TextButton(onClick = { scope.launch { state.load() } }, enabled = !state.busy) { Text("그룹 새로고침") }
            }
        } else {
            GroupHeader(group.name) { state.back() }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                GroupMuted("멤버 ${group.members.size}명 / 최대 30명")
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    GroupButton("멤버 초대", { open(LiveGroupSheet.INVITE) }, Modifier.weight(1f), enabled = !state.busy)
                    GroupButton("이름 변경", { name = group.name; open(LiveGroupSheet.RENAME) }, Modifier.weight(1f), outlined = true, enabled = !state.busy)
                }
                Text("그룹 멤버", style = MaterialTheme.typography.titleMedium)
                group.members.forEach { member ->
                    Text(member.nickname + (if (member.me) " · 나" else "") + (if (member.owner) " · 방장" else ""))
                }
                GroupCard {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("함께 남긴 앨범", style = MaterialTheme.typography.titleMedium)
                        GroupMuted("앨범 기능을 준비하고 있어요.")
                    }
                }
                TextButton(onClick = { scope.launch { state.openGroup(group.id) } }, enabled = !state.busy) { Text("그룹 정보 새로고침") }
            }
        }
        if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (state.sheet == null) state.error?.let { Text(it, Modifier.padding(24.dp), color = MaterialTheme.colorScheme.error) }
    }
    state.sheet?.let { sheet ->
        ModalBottomSheet(onDismissRequest = { state.dismissSheet() }) {
            Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(when (sheet) {
                    LiveGroupSheet.CREATE -> "새 그룹 만들기"
                    LiveGroupSheet.RENAME -> "그룹 이름 변경"
                    LiveGroupSheet.JOIN -> "코드로 참여"
                    LiveGroupSheet.INVITE -> "멤버 초대"
                }, style = MaterialTheme.typography.headlineSmall)
                when (sheet) {
                    LiveGroupSheet.CREATE, LiveGroupSheet.RENAME -> {
                        OutlinedTextField(name, { name = it }, enabled = !state.busy && !(sheet == LiveGroupSheet.CREATE && state.creationUncertain),
                            label = { Text("그룹 이름") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        GroupMuted("앞뒤 공백을 제외하고 1~30자로 입력해 주세요.")
                        if (sheet == LiveGroupSheet.CREATE && state.creationUncertain) {
                            GroupButton("그룹 목록 확인", { scope.launch { state.reviewCreationResult() } }, Modifier.fillMaxWidth(), enabled = !state.busy)
                        } else GroupButton(if (sheet == LiveGroupSheet.CREATE) "그룹 만들기" else "이름 저장", {
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
                            }, Modifier.fillMaxWidth(), enabled = !state.busy)
                        }
                    }
                    LiveGroupSheet.INVITE -> {
                        GroupMuted("멤버 누구나 초대할 수 있어요. 초대 코드는 발급 후 하루 동안 유효해요.")
                        val invitation = state.invitation
                        if (invitation == null) GroupButton("초대 코드 만들기", { scope.launch { state.issueInvitation() } }, Modifier.fillMaxWidth(), enabled = !state.busy)
                        else {
                            Text(invitation.code, style = MaterialTheme.typography.headlineLarge)
                            GroupMuted("만료: ${invitation.expiresAt.date} ${invitation.expiresAt.hour.toString().padStart(2, '0')}:${invitation.expiresAt.minute.toString().padStart(2, '0')}")
                            GroupButton(if (copied) "복사했어요" else "초대 코드 복사", {
                                clipboard.setText(AnnotatedString(invitation.code)); copied = true
                            }, Modifier.fillMaxWidth())
                        }
                    }
                }
                if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = { state.dismissSheet() }, enabled = !state.busy) { Text("닫기") }
            }
        }
    }
}
