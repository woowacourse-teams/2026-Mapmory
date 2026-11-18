package com.mapmory.android

import android.app.NotificationManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.mapmory.shared.app.BackgroundSaveStatus
import com.mapmory.shared.app.BackgroundTripRecordSave
import com.mapmory.shared.app.AppContainer
import com.mapmory.shared.domain.model.TripRecordQuery
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

internal object NotificationReproductionPolicy {
    fun enabled(baseUrl: String): Boolean =
        baseUrl.trimEnd('/') == "https://dev-api.map-mory.com/api/v1"

    fun recordId(baseUrl: String, input: String): Long? =
        input.trim().takeIf { enabled(baseUrl) && it.matches(Regex("[0-9]+")) }
            ?.toLongOrNull()?.takeIf { it > 0 }
}

/** Internal-only local notification navigation evidence, without creating or saving a record. */
internal object NotificationReproduction {
    private const val NotificationId = 670031
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val remainingSeconds = MutableStateFlow<Int?>(null)
    private val result = MutableStateFlow("아직 예약하지 않았어요.")
    private var scheduledJob: Job? = null

    private fun schedule(context: Context, container: AppContainer, input: String) {
        val baseUrl = context.getString(R.string.mapmory_api_base_url)
        val recordId = NotificationReproductionPolicy.recordId(baseUrl, input) ?: return
        val session = container.accountSession ?: return
        val owner = session.state.value
        if (owner.member == null || owner.transitioning) return
        if (scheduledJob?.isActive == true) return
        val appContext = context.applicationContext
        val manager = appContext.getSystemService(NotificationManager::class.java)
        // This ID belongs only to this internal tool, so an older test cannot look like a new result.
        manager.cancel(NotificationId)
        result.value = "30초 후 로컬 알림을 만듭니다. 홈 화면으로 이동하세요."
        scheduledJob = scope.launch {
            try {
                for (seconds in 30 downTo 1) {
                    remainingSeconds.value = seconds
                    delay(1_000)
                }
                if (!NotificationReproductionPolicy.enabled(appContext.getString(R.string.mapmory_api_base_url))) {
                    result.value = "개발 서버에서만 실행할 수 있어요."
                    return@launch
                }
                val currentAccount = session.state.value
                if (currentAccount.generation != owner.generation || currentAccount.member != owner.member || currentAccount.transitioning) {
                    result.value = "계정이 바뀌어 알림을 만들지 않았어요. 현재 계정의 기록을 다시 선택하세요."
                    return@launch
                }
                if (isMapmoryAppInForeground(appContext)) {
                    result.value = "앱이 화면에 있어 기존 정책에 따라 알림을 만들지 않았어요. 다시 예약한 뒤 홈으로 이동하세요."
                    return@launch
                }
                AndroidBackgroundSaveExecution(appContext, "notification-navigation-reproduction").onCompleted(
                    save = BackgroundTripRecordSave(
                        id = NotificationId.toLong(),
                        title = "알림 이동 검증",
                        locationName = "알림 이동 검증용 기존 개발",
                        startDate = "",
                        photoCount = 0,
                        status = BackgroundSaveStatus.SAVING,
                    ),
                    recordId = recordId,
                    serverPushRequested = false,
                )
                result.value = if (manager.activeNotifications.any { it.id == NotificationId }) {
                    "로컬 알림을 등록했어요. 알림창에서 표시와 이동을 직접 확인하세요."
                } else {
                    "알림 등록을 확인하지 못했어요. 사진 완료 알림 설정과 시스템 알림 권한을 확인하세요."
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                result.value = "알림 등록 실패: ${error.javaClass.simpleName}. 설정과 권한을 확인하세요."
            } finally {
                if (scheduledJob === coroutineContext[Job]) {
                    remainingSeconds.value = null
                    scheduledJob = null
                }
            }
        }
    }

    @Composable
    fun Overlay(container: AppContainer) {
        val context = LocalContext.current
        val baseUrl = stringResource(R.string.mapmory_api_base_url)
        if (!NotificationReproductionPolicy.enabled(baseUrl)) return
        val session = container.accountSession ?: return
        val account by session.state.collectAsState()
        val lookupScope = rememberCoroutineScope()
        var opened by remember { mutableStateOf(false) }
        var recordId by remember(account.generation, account.member) { mutableStateOf("") }
        var lookingUp by remember(account.generation, account.member) { mutableStateOf(false) }
        var lookupMessage by remember(account.generation, account.member) { mutableStateOf("") }
        val seconds by remainingSeconds.collectAsState()
        val message by result.collectAsState()
        val editable = seconds == null && !lookingUp && account.member != null && !account.transitioning
        Box(Modifier.fillMaxWidth().statusBarsPadding(), contentAlignment = Alignment.TopStart) {
            TextButton(onClick = { opened = true }) {
                Text(seconds?.let { "알림 재현 (${it}초)" } ?: "알림 재현")
            }
        }
        if (!opened) return
        Dialog(onDismissRequest = { opened = false }) {
            Surface(shape = MaterialTheme.shapes.large) {
                Column(
                    Modifier.padding(16.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("이미 저장된 개발 기록의 알림 이동 검증", style = MaterialTheme.typography.titleLarge)
                    Text("현재 계정의 최근 개발 기록 ID를 조회하거나 직접 입력하세요. 기록을 만들거나 수정하지 않으며, 실제 업로드 완료나 FCM 수신 증거가 아닙니다.")
                    Button(enabled = editable, onClick = {
                        val owner = session.state.value
                        if (!NotificationReproductionPolicy.enabled(baseUrl) || owner.transitioning || owner.member == null) return@Button
                        lookingUp = true
                        lookupMessage = "현재 계정의 최근 기록 ID를 조회하고 있어요."
                        lookupScope.launch {
                            try {
                                val page = container.tripRecordRepository.getTripRecords(TripRecordQuery(size = 10))
                                val currentAccount = session.state.value
                                if (currentAccount.generation != owner.generation || currentAccount.member != owner.member || currentAccount.transitioning) return@launch
                                page.fold(
                                    onSuccess = { records ->
                                        val ids = records.records.map { it.id }.filter { it > 0 }.distinct()
                                        if (ids.isEmpty()) {
                                            lookupMessage = "현재 계정에서 조회된 기록이 없어요. 입력한 ID는 유지했어요."
                                        } else {
                                            if (NotificationReproductionPolicy.recordId(baseUrl, recordId) !in ids) recordId = ids.first().toString()
                                            lookupMessage = "최근 기록 ID: ${ids.joinToString(", ")}"
                                        }
                                    },
                                    onFailure = { lookupMessage = "기록 조회에 실패했어요. 입력한 ID는 유지했어요." },
                                )
                            } catch (error: CancellationException) {
                                throw error
                            } catch (error: Exception) {
                                lookupMessage = "기록 조회에 실패했어요. 입력한 ID는 유지했어요."
                            } finally {
                                lookingUp = false
                            }
                        }
                    }) { Text(if (lookingUp) "기록 ID 조회 중" else "현재 계정의 최근 기록 ID 조회") }
                    if (lookupMessage.isNotEmpty()) Text(lookupMessage)
                    OutlinedTextField(
                        value = recordId,
                        onValueChange = { recordId = it },
                        enabled = editable,
                        label = { Text("개발 기록 ID (양수)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                    )
                    Text("예약 후 홈으로 이동하세요. 알림이 생긴 뒤 앱을 종료하면 콜드 시작을 확인할 수 있어요. 예약 중 프로세스가 종료되면 알림은 생성되지 않아요. 창을 닫아도 예약은 유지됩니다.")
                    Text(seconds?.let { "${it}초 후 로컬 알림 생성 예정" } ?: message)
                    Button(
                        enabled = editable && NotificationReproductionPolicy.recordId(baseUrl, recordId) != null,
                        onClick = { schedule(context, container, recordId) },
                    ) { Text("30초 후 로컬 알림 예약") }
                    if (seconds != null) {
                        TextButton(onClick = {
                            scheduledJob?.cancel()
                            result.value = "예약을 취소했어요. 새 알림은 만들지 않았어요."
                        }) { Text("예약 취소") }
                    }
                    TextButton(onClick = { opened = false }) { Text("닫기, 예약 유지") }
                }
            }
        }
    }
}
