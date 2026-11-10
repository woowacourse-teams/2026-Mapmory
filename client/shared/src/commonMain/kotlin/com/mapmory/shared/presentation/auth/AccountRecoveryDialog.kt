package com.mapmory.shared.presentation.auth

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.mapmory.shared.data.auth.AccountSession
import com.mapmory.shared.data.auth.MemberProvider

/** Overlays the current route so closing or cancelling recovery leaves its view-model and draft mounted. */
@Composable
internal fun AccountRecoveryDialog(
    session: AccountSession?,
    controller: AccountLoginController?,
    launcher: SocialLoginLauncher?,
) {
    val account = session?.state?.collectAsState()?.value
    val login = controller?.state?.collectAsState()?.value ?: LoginUiState()
    val currentAccount = account ?: return
    var dismissedPromptId by remember(session, currentAccount.generation) { mutableLongStateOf(0L) }
    val promptId = currentAccount.recoveryPromptId
    if (promptId == 0L || promptId <= dismissedPromptId) return

    if (!currentAccount.recoveryRequired) {
        AlertDialog(
            onDismissRequest = { dismissedPromptId = promptId },
            title = { Text("연결을 복구했어요") },
            text = { Text("원래 화면으로 돌아왔어요. 중단된 작업은 자동 실행되지 않으니 다시 눌러 주세요.") },
            confirmButton = {
                TextButton(onClick = { dismissedPromptId = promptId }) { Text("확인") }
            },
        )
        return
    }
    val provider = currentAccount.sessionProvider
    val guest = provider == MemberProvider.GUEST
    val retryStoredCredentials = guest || provider == null
    AlertDialog(
        onDismissRequest = { if (!login.busy) dismissedPromptId = promptId },
        title = { Text(if (retryStoredCredentials) "연결을 복구하지 못했어요" else "다시 인증해 주세요") },
        text = {
            androidx.compose.foundation.layout.Column {
                Text(if (guest) {
                    "기존 기록과 작성 내용은 이 기기에 보관되어 있어요. 저장된 게스트 연결을 다시 확인할 수 있지만, 새 게스트 계정은 만들지 않습니다."
                } else if (retryStoredCredentials) {
                    "기존 기록과 작성 내용은 이 기기에 보관되어 있어요. 저장된 연결만 다시 확인하며 새 계정을 만들지 않습니다."
                } else {
                    "현재 화면과 작성 내용은 유지됩니다. 같은 계정을 확인한 뒤에도 요청한 작업은 자동 실행되지 않으니, 복구 후 다시 눌러 주세요."
                })
                login.message?.let { Text(it) }
                if (login.busy) CircularProgressIndicator()
                if (!retryStoredCredentials && launcher?.providers?.none { it.name == provider.name } == true) {
                    Text("현재 계정의 로그인 방식을 이 기기에서 사용할 수 없어요.")
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !login.busy && if (retryStoredCredentials) controller != null else launcher?.providers?.any { it.name == provider.name } == true,
                onClick = {
                    if (retryStoredCredentials) controller?.retryExistingCredentials()
                    else if (launcher != null && controller != null) {
                        controller.reauthenticateSameAccount(
                            launcher,
                            SocialProvider.valueOf(provider.name),
                            onCancelled = { dismissedPromptId = promptId },
                        )
                    }
                },
            ) { Text(if (retryStoredCredentials) "저장된 연결 다시 확인" else "같은 계정으로 다시 인증") }
        },
        dismissButton = {
            TextButton(enabled = !login.busy, onClick = { dismissedPromptId = promptId }) { Text("취소") }
        },
    )
}
