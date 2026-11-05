package com.mapmory.shared.presentation.auth

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mapmory.shared.data.auth.AccountSession
import com.mapmory.shared.data.auth.MemberProvider

@Composable
internal fun AccountSettingsPanel(session: AccountSession, controller: AccountLoginController) {
    val account by session.state.collectAsState()
    val ui by controller.state.collectAsState()
    val launcher = LocalSocialLoginLauncher.current
    var confirmWithdrawal by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    var retry by remember { mutableStateOf(0) }
    var restoreFailed by remember { mutableStateOf(false) }
    LaunchedEffect(session, retry) {
        restoreFailed = session.restore().isFailure
    }
    Column(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        val member = account.member
        if (member != null && member.provider != MemberProvider.GUEST) {
            Text("계정", style = MaterialTheme.typography.titleMedium)
            Text("${member.nickname} · ${when (member.provider) {
                MemberProvider.GOOGLE -> "Google"
                MemberProvider.KAKAO -> "카카오"
                MemberProvider.APPLE -> "Apple"
                else -> "게스트"
            }}", style = MaterialTheme.typography.bodyMedium)
            Row {
            TextButton(onClick = { confirm = true }, enabled = !ui.busy && !account.transitioning && !account.pendingLogin) {
                Text("로그아웃")
            }
            TextButton(onClick = { confirmWithdrawal = true }, enabled = !ui.busy && !account.transitioning && !account.pendingLogin) { Text("회원 탈퇴") }
            }
            ui.message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        } else if (restoreFailed && !account.pendingLogin && !account.pendingLogout) {
            Text("계정 상태를 확인하지 못했어요.")
            TextButton(onClick = { retry++ }) { Text("계정 확인 다시 시도") }
        }
    }
    if (confirmWithdrawal) AlertDialog(
        onDismissRequest = { confirmWithdrawal = false },
        title = { Text("정말 탈퇴할까요?") },
        text = { Text("탈퇴하면 저장한 기록을 다시 볼 수 없으며, 다시 가입해도 복구할 수 없어요.") },
        confirmButton = { TextButton(onClick = { confirmWithdrawal = false; controller.withdraw(launcher) }) { Text("회원 탈퇴") } },
        dismissButton = { TextButton(onClick = { confirmWithdrawal = false }) { Text("취소") } },
    )
    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false },
        title = { Text("로그아웃할까요?") },
        text = { Text("계정의 기록은 서버에 보관돼요. 이 기기의 로그인 정보는 삭제되고, 새 게스트로 개인 기록을 사용할 수 있어요. 계정 기록은 다시 로그인하면 볼 수 있어요.") },
        confirmButton = { TextButton(onClick = { confirm = false; controller.logout() }) { Text("로그아웃") } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("취소") } },
    )
}
