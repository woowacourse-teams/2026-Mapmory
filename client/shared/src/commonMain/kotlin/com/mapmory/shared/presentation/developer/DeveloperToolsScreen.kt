package com.mapmory.shared.presentation.developer

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mapmory.shared.developer.MapmoryDeveloperToolsInfo
import com.mapmory.shared.presentation.triprecord.screen.TripIconButton
import com.mapmory.shared.presentation.triprecord.screen.TripRecordBackground
import com.mapmory.shared.presentation.triprecord.screen.TripRecordPalette
import mapmoryclient.shared.generated.resources.Res
import mapmoryclient.shared.generated.resources.mori_expression_sheet
import org.jetbrains.compose.resources.painterResource

@Composable
internal fun DeveloperToolsScreen(
    info: MapmoryDeveloperToolsInfo,
    isTravelAchievementsEnabled: Boolean,
    onTravelAchievementsEnabledChange: (Boolean) -> Unit,
    isMascotPreviewEnabled: Boolean,
    onMascotPreviewEnabledChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TripRecordBackground(
        modifier = modifier,
        backgroundColor = TripRecordPalette.current.pageBackground,
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().height(60.dp).padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TripIconButton(
                    label = "←",
                    contentDescription = "뒤로가기",
                    onClick = onBack,
                )
                Text(
                    text = "개발자 도구",
                    color = TripRecordPalette.current.headingText,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 20.dp,
                    top = 12.dp,
                    end = 20.dp,
                    bottom = 24.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item {
                    Text(
                        text = "디버그 빌드에서 확인할 수 있는 실행 정보예요.",
                        color = TripRecordPalette.current.secondaryText,
                        fontSize = 13.sp,
                    )
                }
                item {
                    ExperimentalFeaturesCard(
                        isTravelAchievementsEnabled = isTravelAchievementsEnabled,
                        onTravelAchievementsEnabledChange = onTravelAchievementsEnabledChange,
                        isMascotPreviewEnabled = isMascotPreviewEnabled,
                        onMascotPreviewEnabledChange = onMascotPreviewEnabledChange,
                    )
                }
                item {
                    DeveloperInfoCard(
                        title = "앱 정보",
                        rows = listOf(
                            "versionName" to info.versionName,
                            "versionCode" to info.versionCode.toString(),
                            "빌드 유형" to info.buildType,
                        ),
                    )
                }
                item {
                    DeveloperInfoCard(
                        title = "실행 환경",
                        rows = listOf(
                            "서버" to info.apiBaseUrl.toServerLabel(),
                            "기기" to info.deviceName,
                            "Android" to info.androidVersion,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun ExperimentalFeaturesCard(
    isTravelAchievementsEnabled: Boolean,
    onTravelAchievementsEnabledChange: (Boolean) -> Unit,
    isMascotPreviewEnabled: Boolean,
    onMascotPreviewEnabledChange: (Boolean) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = TripRecordPalette.current.surface),
        border = BorderStroke(1.dp, TripRecordPalette.current.border),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "실험실 기능",
                color = TripRecordPalette.current.headingText,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
            )
            ExperimentalFeatureToggle(
                title = "여행 업적",
                description = "통계에서 모리와 업적, 새 배지 축하를 확인해요.",
                checked = isTravelAchievementsEnabled,
                onCheckedChange = onTravelAchievementsEnabledChange,
            )
            ExperimentalFeatureToggle(
                title = "마스코트 시안",
                description = "대표 캐릭터의 표정 시트를 미리 봅니다.",
                checked = isMascotPreviewEnabled,
                onCheckedChange = onMascotPreviewEnabledChange,
            )
            if (isMascotPreviewEnabled) {
                Image(
                    painter = painterResource(Res.drawable.mori_expression_sheet),
                    contentDescription = "맵모리 대표 마스코트의 다섯 가지 표정 시안",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth().height(110.dp),
                )
            }
        }
    }
}

@Composable
private fun ExperimentalFeatureToggle(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = TripRecordPalette.current.bodyText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(
                description,
                color = TripRecordPalette.current.secondaryText,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun DeveloperInfoCard(
    title: String,
    rows: List<Pair<String, String>>,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = TripRecordPalette.current.surface),
        border = BorderStroke(1.dp, TripRecordPalette.current.border),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = title,
                color = TripRecordPalette.current.headingText,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
            )
            rows.forEachIndexed { index, (label, value) ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = label,
                        color = TripRecordPalette.current.secondaryText,
                        fontSize = 11.sp,
                    )
                    Text(
                        text = value,
                        color = TripRecordPalette.current.bodyText,
                        fontSize = 13.sp,
                    )
                }
                if (index != rows.lastIndex) {
                    HorizontalDivider(color = TripRecordPalette.current.line)
                }
            }
        }
    }
}

private fun String.toServerLabel(): String =
    if (contains("dev-api", ignoreCase = true)) "개발 서버" else "운영 서버"
