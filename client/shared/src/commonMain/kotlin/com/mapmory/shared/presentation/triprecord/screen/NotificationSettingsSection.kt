package com.mapmory.shared.presentation.triprecord.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal expect fun NotificationSettingsSection()

@Composable
internal fun NotificationSettingsCard(
    recordSaveEnabled: Boolean,
    travelVideoEnabled: Boolean,
    onRecordSaveChanged: (Boolean) -> Unit,
    onTravelVideoChanged: (Boolean) -> Unit,
    onOpenDeviceSettings: () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier.fillMaxWidth()
            .clip(shape)
            .background(TripRecordPalette.current.surface)
            .border(1.dp, TripRecordPalette.current.border, shape)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "알림",
            color = TripRecordPalette.current.bodyText,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
        )
        NotificationSettingRow(
            title = "사진 기록 알림",
            description = "업로드 진행 상황과 저장 완료를 알려드려요.",
            checked = recordSaveEnabled,
            onCheckedChange = onRecordSaveChanged,
        )
        NotificationSettingRow(
            title = "여행 영상 준비 완료",
            description = "타임라인 영상이 준비되면 알려드려요.",
            checked = travelVideoEnabled,
            onCheckedChange = onTravelVideoChanged,
        )
        Text(
            text = "알림을 꺼도 기록 저장과 영상 생성은 계속돼요.\n기기 알림 권한이 꺼져 있으면 완료 알림만 받을 수 없어요.",
            color = TripRecordPalette.current.secondaryText,
            fontSize = 11.sp,
            lineHeight = 16.sp,
        )
        TextButton(
            onClick = onOpenDeviceSettings,
            contentPadding = PaddingValues(start = 0.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
        ) {
            Text("기기 알림 설정", color = TripRecordPalette.current.primary)
        }
    }
}

@Composable
private fun NotificationSettingRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = TripRecordPalette.current.bodyText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Text(description, color = TripRecordPalette.current.secondaryText, fontSize = 10.sp)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
