package com.mapmory.shared.presentation.auth

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mapmory.shared.presentation.triprecord.screen.TripRecordPalette

@Composable
internal fun SocialLoginPanel() {
    val launcher = LocalSocialLoginLauncher.current ?: return
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var active by remember { mutableStateOf(true) }
    DisposableEffect(launcher) { active = true; onDispose { active = false } }
    Column(Modifier.fillMaxWidth().padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("계정 연결", style = MaterialTheme.typography.titleMedium)
        Text("소셜 로그인 연동을 준비 중이에요. 현재 게스트 기록은 유지돼요.",
            color = TripRecordPalette.current.muted, style = MaterialTheme.typography.bodySmall)
        launcher.providers.forEach { provider ->
            OutlinedButton(onClick = {
                if (busy) return@OutlinedButton
                busy = true; message = null
                launcher.launch(provider) { result ->
                    if (active) {
                        busy = false
                        message = when (result) {
                            is SocialLoginSuccess -> "소셜 인증을 확인했어요. 맵모리 계정 연결은 서버 연동 후 지원돼요."
                            SocialLoginCancelled -> null
                            is SocialLoginFailure -> result.message
                        }
                    }
                }
            }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                Text(when (provider) {
                    SocialProvider.GOOGLE -> "Google로 계속하기"
                    SocialProvider.KAKAO -> "카카오로 계속하기"
                    SocialProvider.APPLE -> "Apple로 계속하기"
                })
            }
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
    }
}
