package com.mapmory.shared.presentation.developer

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mapmory.shared.developer.MapmoryDeveloperToolsInfo
import com.mapmory.shared.presentation.triprecord.screen.TripIconButton
import com.mapmory.shared.presentation.triprecord.screen.TripRecordBackground
import com.mapmory.shared.presentation.triprecord.screen.TripRecordPalette

@Composable
internal fun DeveloperToolsScreen(
    info: MapmoryDeveloperToolsInfo,
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
                    DeveloperInfoCard(
                        title = "앱 정보",
                        rows = listOf(
                            "앱 버전" to info.appVersion,
                            "패키지" to info.applicationId,
                            "빌드 유형" to info.buildType,
                        ),
                    )
                }
                item {
                    DeveloperInfoCard(
                        title = "실행 환경",
                        rows = listOf(
                            "서버" to info.apiBaseUrl.toServerLabel(),
                            "API 주소" to info.apiBaseUrl,
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
