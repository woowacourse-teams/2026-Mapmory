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
import com.mapmory.shared.data.remote.MapmoryApiException
import com.mapmory.shared.data.remote.RemoteGroupSummary
import com.mapmory.shared.analytics.*
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
    onOpenPreview: (() -> Unit)? = null,
) {
    val session = container.accountSession
    val account = session?.state?.collectAsState()?.value
    val analytics = LocalMapmoryAnalytics.current
    var retry by remember { mutableStateOf(0) }
    var restoring by remember { mutableStateOf(true) }
    var restoreError by remember { mutableStateOf<String?>(null) }
    var groups by remember { mutableStateOf<List<RemoteGroupSummary>?>(null) }
    var listError by remember { mutableStateOf<String?>(null) }
    var loginRequired by remember { mutableStateOf(false) }
    var noticeDismissed by remember { mutableStateOf(false) }
    LaunchedEffect(session, retry) {
        session?.state?.first { !it.transitioning }
        restoring = true; restoreError = null; groups = null; listError = null; loginRequired = false
        val result = session?.restore()
        val member = result?.getOrNull()
        restoring = false
        if (member == null) {
            if (account?.pendingLogin != true) restoreError = "계정 상태를 확인하지 못했어요. 다시 시도해 주세요."
            return@LaunchedEffect
        }
        if (member.provider != MemberProvider.GUEST) {
            val started = kotlin.time.TimeSource.Monotonic.markNow()
            val loaded = container.groupRepository?.groups()
            groups = loaded?.getOrNull()
            if (loaded?.isFailure == true) {
                val error = loaded.exceptionOrNull()!!
                loginRequired = (error as? MapmoryApiException)?.code == "SOCIAL_LOGIN_REQUIRED"
                listError = if ((error as? MapmoryApiException)?.statusCode == 404)
                    "그룹 기능을 준비하고 있어요. 잠시 후 다시 확인해 주세요." else "그룹을 불러오지 못했어요. 다시 시도해 주세요."
                if (!loginRequired) analytics.reportFailure("group_list", error, mapOf("source" to "group_tab", "execution_mode" to "live"))
            }
            analytics.emit("group_list_loaded", mapOf("source" to "group_tab", "execution_mode" to "live",
                "outcome" to if (groups != null) "success" else if (loginRequired) "login_required" else "failure",
                "duration_ms" to started.elapsedNow().inWholeMilliseconds.toString()))
        }
    }
    TripRecordBackground(modifier) {
        Column(Modifier.fillMaxSize()) {
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
                Text("그룹", Modifier.padding(24.dp), style = MaterialTheme.typography.headlineMedium)
                onOpenPreview?.let { open ->
                    TextButton(onClick = open, modifier = Modifier.padding(horizontal = 16.dp)) {
                        Text("그룹 앨범 화면 미리보기")
                    }
                }
                when {
                    account?.pendingLogin == true -> SocialLoginPanel(session, container.accountLoginController)
                    restoring -> CircularProgressIndicator(Modifier.padding(24.dp))
                    restoreError != null -> Column(Modifier.padding(24.dp)) {
                        Text(restoreError!!)
                        TextButton(onClick = { retry++ }) { Text("계정 확인 다시 시도") }
                        // A lost/invalid session must still offer deliberate social recovery.
                        SocialLoginPanel(session, container.accountLoginController)
                    }
                    account?.member?.provider == MemberProvider.GUEST || loginRequired -> SocialLoginPanel(session, container.accountLoginController)
                    listError != null -> Column(Modifier.padding(24.dp)) {
                        Text(listError!!)
                        TextButton(onClick = { retry++ }) { Text("그룹 다시 불러오기") }
                    }
                    groups == null -> CircularProgressIndicator(Modifier.padding(24.dp))
                    groups!!.isEmpty() -> Text("아직 참여한 그룹이 없어요.", Modifier.padding(24.dp))
                    else -> groups!!.forEach { group ->
                        Card(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 6.dp)) {
                            Column(Modifier.padding(20.dp)) {
                                Text(group.name, style = MaterialTheme.typography.titleMedium)
                                Text("멤버 ${group.memberCount}명 · 앨범 ${group.albumCount}개")
                            }
                        }
                    }
                }
            }
            TripBottomBar(selected = TripBottomTab.GROUP, onMapClick = onMapClick,
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
