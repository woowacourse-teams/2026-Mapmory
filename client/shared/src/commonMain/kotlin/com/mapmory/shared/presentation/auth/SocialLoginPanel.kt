package com.mapmory.shared.presentation.auth

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mapmory.shared.analytics.LocalMapmoryAnalytics
import com.mapmory.shared.presentation.triprecord.screen.TripRecordPalette
import com.mapmory.shared.data.auth.AccountSession

@Composable
internal fun SocialLoginPanel(
    session: AccountSession? = null,
    controller: AccountLoginController? = null,
) {
    val palette = TripRecordPalette.current
    val launcher = LocalSocialLoginLauncher.current
    val analytics = LocalMapmoryAnalytics.current
    val measured = remember(launcher, analytics) { launcher?.let { AnalyticsSocialLoginLauncher(it, analytics, "group_tab") } }
    LaunchedEffect(measured) { measured?.panelViewed() }
    val ui = controller?.state?.collectAsState()?.value ?: LoginUiState()
    val account = session?.state?.collectAsState()?.value
    var recoveryProvider by remember { mutableStateOf<SocialProvider?>(null) }
    var previewMessage by remember { mutableStateOf<String?>(null) }
    recoveryProvider?.let { provider ->
        AlertDialog(onDismissRequest = { recoveryProvider = null },
            title = { Text("계정으로 다시 로그인할까요?") },
            text = { Text("현재 세션을 복구하지 못했어요. 기존 소셜 계정의 기록은 다시 불러올 수 있지만, 게스트 기록을 이 계정에 연결하는 것은 보장할 수 없어요.") },
            confirmButton = { TextButton(onClick = {
                recoveryProvider = null
                if (measured != null) controller?.launch(measured, provider, recoverInvalidSession = true)
            }) { Text("계정으로 로그인") } },
            dismissButton = { TextButton(onClick = { recoveryProvider = null }) { Text("취소") } })
    }
    Column(Modifier.fillMaxWidth().padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("계정 연결", style = MaterialTheme.typography.titleMedium)
        Text("처음 연결하는 계정은 지금 기록을 이어서 사용할 수 있어요. 이미 사용한 계정으로 로그인하면 해당 계정의 기록으로 전환돼요.",
            color = palette.muted, style = MaterialTheme.typography.bodySmall)
        if (account?.pendingLogin == true) {
            Text("이전 로그인 확인이 필요해요. 먼저 결과 확인을 눌러 주세요. 앱을 다시 실행했다면 이전과 같은 방식과 계정으로 인증해 주세요.")
            if (account.needsProviderProof.not()) {
                Button(onClick = { controller?.resume() }, enabled = !ui.busy) { Text("로그인 결과 확인") }
            }
        }
        measured?.providers?.filter { account?.recoveryProvider == null || it.name == account.recoveryProvider.name }?.forEach { provider ->
            if (account?.recentLoginProvider?.name == provider.name) {
                Text("최근 로그인", color = palette.primary, style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.align(androidx.compose.ui.Alignment.End))
            }
            SocialLoginButton(provider = provider, onClick = {
                if (controller != null) {
                    if (account?.recoveryRequired == true) recoveryProvider = provider
                    else controller.launch(measured, provider)
                }
                else measured.launch(provider) { result ->
                    previewMessage = when (result) {
                        is SocialLoginFailure -> result.message
                        is SocialLoginSuccess -> "인증을 확인했어요. 서버 연결을 사용할 수 없어요."
                        else -> null
                    }
                }
            }, enabled = !ui.busy && account?.transitioning != true &&
                    (account?.pendingLogin != true || account.needsProviderProof),
                modifier = Modifier.fillMaxWidth())
        }
        if (measured == null) Text("이 기기에서 로그인 화면을 준비하지 못했어요.")
        if (ui.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        (ui.message ?: previewMessage)?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
    }
}
