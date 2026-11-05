package com.mapmory.shared.presentation.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.Alignment
import com.mapmory.shared.presentation.triprecord.screen.TripRecordPalette
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
    var restoreFailed by remember { mutableStateOf(false) }
    LaunchedEffect(session) {
        restoreFailed = session.restore().isFailure
    }
    val palette = TripRecordPalette.current
    Column(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        val member = account.member
        if (member != null && member.provider != MemberProvider.GUEST) {
            Column(Modifier.fillMaxWidth().background(palette.surface, RoundedCornerShape(16.dp)).border(1.dp, palette.border, RoundedCornerShape(16.dp)).padding(16.dp)) {
                Text("내 정보", color = palette.headingText, style = MaterialTheme.typography.titleMedium)
                Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(member.nickname, color = palette.headingText, style = MaterialTheme.typography.bodyMedium)
                        val provider = when (member.provider) {
                            MemberProvider.GOOGLE -> "Google"
                            MemberProvider.KAKAO -> "카카오"
                            MemberProvider.APPLE -> "Apple"
                            else -> "게스트"
                        }
                        Text("${provider}로 로그인", color = palette.muted, style = MaterialTheme.typography.bodySmall)
                    }
                    Canvas(Modifier.size(20.dp).background(palette.softSurface, CircleShape).semantics { contentDescription = "기본 프로필 이미지" }) {
                        drawCircle(palette.primary, radius = size.width * 0.17f, center = Offset(size.width / 2, size.height * 0.35f))
                        drawCircle(palette.primary, radius = size.width * 0.3f, center = Offset(size.width / 2, size.height * 0.85f))
                    }
                }
                Row {
                    TextButton(onClick = { confirm = true }, enabled = !ui.busy && !account.transitioning && !account.pendingLogin) {
                        Text("로그아웃")
                    }
                    TextButton(onClick = { confirmWithdrawal = true }, enabled = !ui.busy && !account.transitioning && !account.pendingLogin) {
                        Text("회원 탈퇴")
                    }
                }
            }
            ui.message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        } else if (restoreFailed && !account.pendingLogin && !account.pendingLogout) {
            Text("계정 정보를 불러오지 못했어요. 연결 상태를 확인한 뒤 화면을 다시 열어 주세요.", color = palette.muted, style = MaterialTheme.typography.bodySmall)
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
