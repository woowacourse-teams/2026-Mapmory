package com.mapmory.shared.presentation.triprecord.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mapmory.shared.LocalMapmoryTheme
import com.mapmory.shared.LocalTeamDevice
import com.mapmory.shared.PrivacyPolicy
import com.mapmory.shared.TeamDeviceState
import com.mapmory.shared.TeamDeviceTapCounter
import com.mapmory.shared.analytics.LocalMapmoryAnalytics
import com.mapmory.shared.analytics.MapmoryAnalyticsEvent
import com.mapmory.shared.developer.MapmoryDeveloperToolsInfo
import com.mapmory.shared.domain.model.ServiceFeedbackCategory
import com.mapmory.shared.presentation.feedback.ServiceFeedbackDialog
import com.mapmory.shared.presentation.feedback.currentServiceFeedbackPlatform
import com.mapmory.shared.presentation.privacy.PrivacyPolicyContent
import kotlinx.coroutines.delay

@Composable
internal fun TripSettingsScreen(
    onMapClick: () -> Unit,
    onRecordClick: () -> Unit,
    onProfileClick: () -> Unit,
    onSettingsClick: () -> Unit,
    developerToolsInfo: MapmoryDeveloperToolsInfo?,
    onOpenDeveloperTools: () -> Unit,
    onSubmitFeedback: suspend (ServiceFeedbackCategory, String) -> Result<Unit>,
    modifier: Modifier = Modifier,
) {
    var showFeedback by remember { mutableStateOf(false) }
    var showPrivacyPolicy by remember { mutableStateOf(false) }
    val theme = LocalMapmoryTheme.current
    val analytics = LocalMapmoryAnalytics.current
    val teamDevice = LocalTeamDevice.current

    TripRecordBackground(modifier = modifier, backgroundColor = TripRecordPalette.current.pageBackground) {
        Column(
            Modifier
                .fillMaxSize()
                .background(TripRecordPalette.current.pageBackground),
        ) {
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
            ) {
                Column(Modifier.padding(vertical = 18.dp)) {
                    Text(
                        text = "SETTINGS",
                        color = TripRecordPalette.current.primary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.2.sp,
                    )
                    Text(
                        text = "설정",
                        color = TripRecordPalette.current.headingText,
                        fontSize = 25.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }

                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(TripRecordPalette.current.surface)
                        .border(1.dp, TripRecordPalette.current.border, RoundedCornerShape(16.dp))
                        .padding(16.dp),
                ) {
                    Text(
                        text = "화면 테마",
                        color = TripRecordPalette.current.bodyText,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ThemeOption(
                            label = "라이트 모드",
                            selected = !theme.isDark,
                            onClick = {
                                analytics.logEvent(
                                    MapmoryAnalyticsEvent.THEME_CHANGED,
                                    mapOf("theme" to "light"),
                                )
                                theme.onThemeChange(false)
                            },
                            modifier = Modifier.weight(1f),
                        )
                        ThemeOption(
                            label = "다크 모드",
                            selected = theme.isDark,
                            onClick = {
                                analytics.logEvent(
                                    MapmoryAnalyticsEvent.THEME_CHANGED,
                                    mapOf("theme" to "dark"),
                                )
                                theme.onThemeChange(true)
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))
                NotificationSettingsSection()
                Spacer(Modifier.height(12.dp))
                SettingsAction(
                    title = "의견 보내기",
                    description = "불편한 점이나 의견을 알려주세요.",
                    onClick = { showFeedback = true },
                )
                if (PrivacyPolicy.URL.isNotBlank()) {
                    Spacer(Modifier.height(12.dp))
                    SettingsAction(
                        title = "개인정보 처리방침",
                        description = "서비스의 개인정보 처리 내용을 확인해요.",
                        onClick = {
                            analytics.logEvent(MapmoryAnalyticsEvent.PRIVACY_POLICY_OPENED)
                            showPrivacyPolicy = true
                        },
                    )
                }
                if (developerToolsInfo != null) {
                    Spacer(Modifier.height(12.dp))
                    SettingsAction(
                        title = "개발자 도구",
                        description = "개발 환경 정보를 확인해요.",
                        onClick = onOpenDeveloperTools,
                    )
                }
                if (teamDevice.appVersion.isNotBlank()) {
                    AppVersionRow(teamDevice, Modifier.padding(top = 20.dp, bottom = 24.dp))
                }
            }

            TripBottomBar(
                selected = TripBottomTab.SETTINGS,
                onMapClick = onMapClick,
                onRecordClick = onRecordClick,
                onProfileClick = onProfileClick,
                onSettingsClick = onSettingsClick,
                backgroundColor = TripRecordPalette.current.pageBackground,
                dividerColor = TripRecordPalette.current.navigationDivider,
                selectedIconColor = TripRecordPalette.current.primary,
                selectedLabelColor = TripRecordPalette.current.navigationSelectedLabel,
                unselectedColor = TripRecordPalette.current.navigationUnselected,
            )
        }
    }

    if (showFeedback) {
        ServiceFeedbackDialog(
            platform = currentServiceFeedbackPlatform(),
            onDismiss = { showFeedback = false },
            onSubmit = onSubmitFeedback,
        )
    }

    if (showPrivacyPolicy) {
        Dialog(
            onDismissRequest = { showPrivacyPolicy = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .background(TripRecordPalette.current.pageBackground),
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(TripRecordPalette.current.surface)
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = { showPrivacyPolicy = false }) {
                        Text("닫기", color = TripRecordPalette.current.primary)
                    }
                    Text(
                        text = "개인정보 처리방침",
                        color = TripRecordPalette.current.headingText,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                PrivacyPolicyContent(
                    url = PrivacyPolicy.URL,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                )
            }
        }
    }
}

@Composable
private fun SettingsAction(
    title: String,
    description: String,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(TripRecordPalette.current.surface)
            .border(1.dp, TripRecordPalette.current.border, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(16.dp),
    ) {
        Text(
            text = title,
            color = TripRecordPalette.current.bodyText,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = description,
            color = TripRecordPalette.current.secondaryText,
            fontSize = 11.sp,
            modifier = Modifier.padding(top = 5.dp),
        )
    }
}

@Composable
private fun ThemeOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(11.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(if (selected) TripRecordPalette.current.primarySoft else TripRecordPalette.current.surface)
            .border(
                width = 1.dp,
                color = if (selected) TripRecordPalette.current.primary else TripRecordPalette.current.border,
                shape = shape,
            )
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = label,
            color = if (selected) TripRecordPalette.current.primary else TripRecordPalette.current.bodyText,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun AppVersionRow(teamDevice: TeamDeviceState, modifier: Modifier = Modifier) {
    val tapCounter = remember { TeamDeviceTapCounter() }
    var notice by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(notice) {
        if (notice != null) {
            delay(TeamDeviceNoticeMillis)
            notice = null
        }
    }
    Column(modifier) {
        Text(
            text = "앱 버전 ${teamDevice.appVersion}",
            color = TripRecordPalette.current.secondaryText,
            fontSize = 11.sp,
            modifier = Modifier.clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null,
                role = Role.Button,
            ) {
                if (tapCounter.tap()) {
                    val enabled = !teamDevice.isTeamDevice
                    teamDevice.onTeamDeviceChange(enabled)
                    notice = if (enabled) "팀원 기기 모드를 켰어요" else "팀원 기기 모드를 껐어요"
                }
            },
        )
        notice?.let { message ->
            Text(
                text = message,
                color = TripRecordPalette.current.contentOnMedia,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .padding(top = 8.dp)
                    .background(TripRecordPalette.current.mediaScrim, RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
    }
}

private const val TeamDeviceNoticeMillis = 2_000L
