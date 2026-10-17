package com.mapmory.shared.presentation.triprecord.screen

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

@Composable
internal fun CurrentLocationConsentDialog(
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        containerColor = TripRecordPalette.current.surface,
        title = { Text("현재 위치 사용 동의", color = TripRecordPalette.current.headingText) },
        text = {
            Text(
                "현재 위치로 지도 중심을 옮기거나 사진을 찾을 시·군·구를 선택합니다. " +
                    "좌표는 기기에서 지역을 찾는 데만 사용하고 Mapmory 서버로 보내거나 저장하지 않습니다. " +
                    "선택한 지역은 기록을 저장할 때 기록 정보로 서버에 전송됩니다. " +
                    "동의하지 않아도 장소를 직접 검색할 수 있어요.",
                color = TripRecordPalette.current.bodyText,
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("동의하고 계속", color = TripRecordPalette.current.primary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text("취소", color = TripRecordPalette.current.secondaryText)
            }
        },
    )
}
