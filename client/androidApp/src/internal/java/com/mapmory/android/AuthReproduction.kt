package com.mapmory.android

import android.content.Context
import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.mapmory.shared.app.AppContainer
import com.mapmory.shared.data.auth.AndroidAuthTokenStore
import io.ktor.client.HttpClient
import io.ktor.client.call.save
import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.plugin
import io.ktor.client.plugins.timeout
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import io.ktor.http.content.TextContent
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONObject
import java.net.ConnectException

/** Compiled only into the internal APK; credentials never leave the existing auth store. */
internal object AuthReproduction {
    private lateinit var client: HttpClient
    private lateinit var store: AndroidAuthTokenStore
    private var base = ""
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val lines = MutableStateFlow(listOf<String>())
    private val busy = MutableStateFlow(false)
    private val blockRefresh = MutableStateFlow(false)
    private val counts = MutableStateFlow(Counts())
    private val refreshGate = RefreshTransmissionGate()

    private data class Counts(val refreshSends: Int = 0, val refreshResponses: Int = 0, val blocked: Int = 0)

    fun attach(appContext: Context, httpClient: HttpClient) {
        base = appContext.getString(R.string.mapmory_api_base_url).trimEnd('/')
        if (!AuthReproductionPolicy.enabled(base)) return
        client = httpClient
        store = AndroidAuthTokenStore(appContext, base)
        // Pause before HttpSend starts the normal request timeout, after a real 401 triggers refresh.
        client.requestPipeline.intercept(HttpRequestPipeline.Before) {
            if (AuthReproductionPolicy.isRefresh(context.url.buildString())) {
                when (val result = refreshGate.awaitIfArmed {
                    record("refresh PAUSED before transmission; resume within 90 seconds")
                }) {
                    RefreshTransmissionGate.Result.NOT_ARMED -> Unit
                    RefreshTransmissionGate.Result.RESUMED -> record("refresh RESUMED; real transport follows")
                    else -> {
                        record("refresh $result before transmission; request NOT sent")
                        throw CancellationException("Internal refresh pause ended without transmission")
                    }
                }
            }
        }
        // AppContainer installs auth retry first, so this inner interceptor sees every actual attempt.
        client.plugin(HttpSend).intercept { request ->
            val requestUrl = request.url.buildString()
            val refresh = AuthReproductionPolicy.isRefresh(requestUrl)
            val me = AuthReproductionPolicy.isMe(requestUrl)
            if (refresh && blockRefresh.value) {
                counts.update { it.copy(blocked = it.blocked + 1) }
                record("refresh BLOCKED before transmission (controlled transport fault)")
                throw ConnectException("Controlled refresh transport failure")
            }
            if (refresh) {
                counts.update { it.copy(refreshSends = it.refreshSends + 1) }
                record("refresh SEND")
            }
            val call = try {
                execute(request)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (refresh) record("refresh TRANSPORT FAILED ${error.javaClass.simpleName}")
                throw error
            }
            if (refresh) counts.update { it.copy(refreshResponses = it.refreshResponses + 1) }
            val responseUrl = call.request.url.toString()
            if ((refresh && AuthReproductionPolicy.isRefresh(responseUrl)) ||
                (me && AuthReproductionPolicy.isMe(responseUrl))) {
                if (call.response.status.value == 401) {
                    val saved = call.save()
                    val code = runCatching { JSONObject(saved.response.bodyAsText()).optString("code") }
                        .getOrNull()?.takeIf { it.matches(Regex("[A-Z_]{1,64}")) } ?: "UNKNOWN_CODE"
                    record("${if (refresh) "refresh" else "me"} HTTP 401 $code")
                    return@intercept saved
                }
                record("${if (refresh) "refresh" else "me"} HTTP ${call.response.status.value}")
            }
            call
        }
    }

    private fun record(message: String) {
        val line = "${System.currentTimeMillis()} $message"
        lines.update { (it + line).takeLast(60) }
        Log.i("AuthReproduction", line)
    }

    private fun runOperation(operation: suspend () -> Unit) {
        if (!busy.compareAndSet(false, true)) return
        scope.launch {
            try {
                check(AuthReproductionPolicy.enabled(base))
                operation()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                // Exception messages and HTTP bodies can include credentials or account data.
                record("operation FAILED ${error.javaClass.simpleName}")
            } finally {
                busy.value = false
            }
        }
    }

    private suspend fun probe(container: AppContainer, simultaneous: Int) {
        val token = checkNotNull(store.load()).accessToken
        val expiry = AuthReproductionPolicy.expiry(token)
        // Only this internal probe gets time for manual offline setup. Real repositories keep 20 seconds.
        val probeTimeout = if (refreshGate.state.value != RefreshTransmissionGate.State.IDLE) 120_000L else 20_000L
        record("probe START count=$simultaneous expired=${expiry?.let { it <= System.currentTimeMillis() / 1000 }}")
        record("probe timeout=${probeTimeout}ms (includes internal pause when armed)")
        coroutineScope {
            val start = CompletableDeferred<Unit>()
            val requests = List(simultaneous) {
                async {
                    start.await()
                    try {
                        val status = client.get("$base/members/me") {
                            bearerAuth(token)
                            header(HttpHeaders.CacheControl, "no-store")
                            timeout { requestTimeoutMillis = probeTimeout }
                        }.status.value
                        record("probe END HTTP $status")
                        status in 200..299
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        record("probe END ${error.javaClass.simpleName}")
                        false
                    }
                }
            }
            start.complete(Unit)
            if (requests.awaitAll().any { !it }) {
                // Reuse the session boundary to mark terminal recovery; never alter local tokens.
                val session = checkNotNull(container.accountSession)
                session.restore()
                session.requestRecoveryPrompt()
                record("session recoveryRequired=${session.state.value.recoveryRequired}")
            }
        }
    }

    @Composable
    fun Overlay(container: AppContainer) {
        if (!AuthReproductionPolicy.enabled(base)) return
        val session = container.accountSession ?: return
        var opening by remember(session) { mutableStateOf<Any?>(null) }
        var expiry by remember { mutableStateOf<Long?>(null) }
        var now by remember { mutableLongStateOf(System.currentTimeMillis() / 1000) }
        val isBusy by busy.collectAsState()
        val blocked by blockRefresh.collectAsState()
        val log by lines.collectAsState()
        val totals by counts.collectAsState()
        val gate by refreshGate.state.collectAsState()
        LaunchedEffect(Unit) {
            while (isActive) {
                now = System.currentTimeMillis() / 1000
                expiry = AuthReproductionPolicy.expiry(runCatching { store.load()?.accessToken }.getOrNull())
                delay(1000)
            }
        }
        Box(Modifier.fillMaxWidth().statusBarsPadding(), contentAlignment = Alignment.TopEnd) {
            TextButton(onClick = { opening = Any() }) { Text("인증 재현") }
        }
        val activeOpening = opening ?: return
        val account by key(activeOpening) { session.state.collectAsState() }
        var confirmation by remember(activeOpening, account) {
            mutableStateOf<AuthReproductionPolicy.RevocationConfirmation?>(null)
        }
        val closeDialog = {
            opening = null
            confirmation = null
            refreshGate.cancel()
            Unit
        }
        Dialog(onDismissRequest = closeDialog) {
            Surface(shape = MaterialTheme.shapes.large) {
                Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Internal 인증 재현", style = MaterialTheme.typography.titleLarge)
                    Text("개발 서버 전용. JWT 수정, 가짜 401 없음. 세션 준비 후 앱을 켜 둔 채 실제 만료를 기다리세요.")
                    Text("exp=${expiry ?: "없음"}, 남은 시간=${expiry?.minus(now) ?: "없음"}초 (표시 전용)")
                    Text("갱신 전송=${totals.refreshSends}, 응답=${totals.refreshResponses}, 모의 차단=${totals.blocked}")
                    Text("복구 필요=${account.recoveryRequired}, 요청 중=$isBusy")
                    Text("실제 갱신 전 대기=$gate (최대 90초, 취소 시 요청하지 않음)")
                    Button(enabled = !isBusy && !blocked && gate == RefreshTransmissionGate.State.IDLE, onClick = {
                        if (refreshGate.arm()) record("refresh pause ARMED for next real refresh")
                    }) { Text("다음 실제 갱신 직전 대기") }
                    if (gate != RefreshTransmissionGate.State.IDLE) {
                        Button(enabled = gate == RefreshTransmissionGate.State.PAUSED, onClick = { refreshGate.resume() }) {
                            Text("갱신 재개, 실제 통신 시작")
                        }
                        TextButton(onClick = { refreshGate.cancel() }) { Text("갱신 대기 취소, 전송 안 함") }
                    }
                    Button(enabled = !isBusy, onClick = {
                        runOperation {
                            record("session PREPARE")
                            record("session ready=${session.restore().isSuccess}")
                        }
                    }) { Text("세션 준비 / 상태 조회") }
                    Button(enabled = !isBusy, onClick = { runOperation { probe(container, 1) } }) { Text("실제 /me 1회") }
                    Button(enabled = !isBusy, onClick = { runOperation { probe(container, 3) } }) { Text("동일 토큰으로 /me 3회 동시") }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = blocked, enabled = !isBusy && gate == RefreshTransmissionGate.State.IDLE, onCheckedChange = {
                            blockRefresh.value = it
                            record("controlled refresh transport fault=$it (not device offline)")
                        })
                        Text("갱신만 전송 전 모의 차단 (실제 오프라인 아님)", Modifier.weight(1f))
                    }
                    Text("차단 해제 후 재시도는 기존 60초 대기 정책을 따릅니다.")
                    val currentAccount = account
                    if (AuthReproductionPolicy.hasStableAccount(currentAccount)) {
                        val enteredText = confirmation?.takeIf { it.isCurrent(activeOpening, currentAccount) }?.text.orEmpty()
                        OutlinedTextField(
                            value = enteredText,
                            onValueChange = { text ->
                                confirmation = if (opening === activeOpening && session.state.value === currentAccount) {
                                    AuthReproductionPolicy.RevocationConfirmation(text, activeOpening, currentAccount)
                                } else null
                            },
                            enabled = !isBusy,
                            label = { Text("폐기용 세션일 때만 ‘폐기용 세션’ 입력") },
                        )
                        Text("서버 로그아웃은 이 세션을 실제 폐기합니다. 개인 계정으로 실행하지 마세요. 로컬 기록과 토큰은 지우지 않습니다.")
                        Button(
                            enabled = !isBusy && AuthReproductionPolicy.canRevoke(base, confirmation, activeOpening, currentAccount),
                            onClick = {
                                val confirmed = confirmation
                                confirmation = null
                                runOperation {
                                    val stillConfirmed = {
                                        AuthReproductionPolicy.canRevoke(base, confirmed, opening, session.state.value)
                                    }
                                    if (!stillConfirmed()) return@runOperation
                                    val refreshToken = checkNotNull(store.load()).refreshToken
                                    if (!stillConfirmed()) return@runOperation
                                    val response = client.post("$base/auth/logout") {
                                        timeout { requestTimeoutMillis = 20_000 }
                                        setBody(TextContent(JSONObject().put("refreshToken", refreshToken).toString(), ContentType.Application.Json))
                                    }
                                    record("disposable server logout HTTP ${response.status.value}; local credentials retained")
                                }
                            },
                        ) { Text("폐기용 세션만 서버 로그아웃") }
                    }
                    TextButton(enabled = !isBusy, onClick = { counts.value = Counts(); lines.value = emptyList() }) { Text("계측 초기화") }
                    TextButton(onClick = closeDialog) { Text("닫기, 현재 화면 유지") }
                    Text(log.joinToString("\n"), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
