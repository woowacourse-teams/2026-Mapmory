package com.mapmory.shared.presentation.account

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mapmory.shared.presentation.triprecord.screen.TripRecordPalette
import com.mapmory.shared.presentation.triprecord.screen.ProvideTripRecordPalettes
import com.mapmory.shared.preview.PreviewSurface

@Composable
internal fun AccountConnectionScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showUnavailableNotice by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(TripRecordPalette.current.pageBackground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 18.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Mapmory",
                color = TripRecordPalette.current.headingText,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            TextButton(onClick = onBack) {
                Text("닫기", color = TripRecordPalette.current.secondaryText)
            }
        }

        Spacer(Modifier.height(22.dp))
        TravelMemoryArtwork(Modifier.fillMaxWidth().height(194.dp))
        Spacer(Modifier.height(30.dp))

        Text(
            text = "KEEP YOUR JOURNEY",
            color = TripRecordPalette.current.primary,
            fontSize = 11.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 1.8.sp,
        )
        Text(
            text = "여행의 기억을\n안전하게 이어가요",
            modifier = Modifier.padding(top = 12.dp),
            color = TripRecordPalette.current.headingText,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            lineHeight = 36.sp,
        )
        Text(
            text = "기존 여행 기록을 안전하게 이어갈 수 있는 계정 연결 방식을 준비하고 있어요.",
            modifier = Modifier.padding(top = 12.dp),
            color = TripRecordPalette.current.bodyText,
            style = MaterialTheme.typography.bodyLarge,
            lineHeight = 25.sp,
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(TripRecordPalette.current.surface)
                .border(1.dp, TripRecordPalette.current.border, RoundedCornerShape(16.dp))
                .padding(16.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(TripRecordPalette.current.primarySoft),
                contentAlignment = Alignment.Center,
            ) {
                Text("i", color = TripRecordPalette.current.primary, fontWeight = FontWeight.Bold)
            }
            Column {
                Text(
                    text = "기존 기록 보존을 먼저 확인하고 있어요",
                    color = TripRecordPalette.current.headingText,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "기록이 안전하게 이어지는 방식을 확인한 뒤 연결 기능을 열게요.",
                    modifier = Modifier.padding(top = 4.dp),
                    color = TripRecordPalette.current.secondaryText,
                    style = MaterialTheme.typography.bodySmall,
                    lineHeight = 19.sp,
                )
            }
        }

        Spacer(Modifier.height(26.dp))
        Button(
            onClick = { showUnavailableNotice = true },
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = KakaoYellow,
                contentColor = KakaoText,
            ),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                KakaoMark()
                Text(
                    text = "카카오 계정으로 연결",
                    modifier = Modifier.padding(start = 10.dp),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        Text(
            text = "계정 연결은 현재 준비 중이에요.",
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            color = TripRecordPalette.current.secondaryText,
            style = MaterialTheme.typography.bodySmall,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
    }

    if (showUnavailableNotice) {
        AlertDialog(
            onDismissRequest = { showUnavailableNotice = false },
            containerColor = TripRecordPalette.current.surface,
            title = { Text("아직 연결할 수 없어요", color = TripRecordPalette.current.headingText) },
            text = {
                Text(
                    "기존 여행 기록이 안전하게 이어지는 방식을 확인한 뒤 계정 연결을 열게요.",
                    color = TripRecordPalette.current.bodyText,
                )
            },
            confirmButton = {
                TextButton(onClick = { showUnavailableNotice = false }) {
                    Text("확인", color = TripRecordPalette.current.primary)
                }
            },
        )
    }
}

@Composable
private fun KakaoMark() {
    Canvas(Modifier.size(22.dp)) {
        val bubble = Path().apply {
            moveTo(size.width * 0.5f, size.height * 0.08f)
            cubicTo(size.width * 0.23f, size.height * 0.08f, size.width * 0.07f, size.height * 0.25f, size.width * 0.07f, size.height * 0.48f)
            cubicTo(size.width * 0.07f, size.height * 0.7f, size.width * 0.23f, size.height * 0.85f, size.width * 0.5f, size.height * 0.85f)
            cubicTo(size.width * 0.77f, size.height * 0.85f, size.width * 0.93f, size.height * 0.7f, size.width * 0.93f, size.height * 0.48f)
            cubicTo(size.width * 0.93f, size.height * 0.25f, size.width * 0.77f, size.height * 0.08f, size.width * 0.5f, size.height * 0.08f)
            close()
        }
        drawPath(bubble, Color(0xFF191919))
        drawLine(Color.White, Offset(size.width * 0.29f, size.height * 0.43f), Offset(size.width * 0.71f, size.height * 0.43f), 1.5.dp.toPx(), cap = StrokeCap.Round)
        drawLine(Color.White, Offset(size.width * 0.35f, size.height * 0.57f), Offset(size.width * 0.65f, size.height * 0.57f), 1.5.dp.toPx(), cap = StrokeCap.Round)
    }
}

@Composable
private fun TravelMemoryArtwork(modifier: Modifier = Modifier) {
    val palette = TripRecordPalette.current
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(palette.softSurface)
            .border(1.dp, palette.border, RoundedCornerShape(20.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize().padding(horizontal = 30.dp, vertical = 26.dp)) {
            val route = Path().apply {
                moveTo(size.width * 0.16f, size.height * 0.7f)
                cubicTo(size.width * 0.34f, size.height * 0.82f, size.width * 0.39f, size.height * 0.2f, size.width * 0.57f, size.height * 0.37f)
                cubicTo(size.width * 0.71f, size.height * 0.5f, size.width * 0.77f, size.height * 0.23f, size.width * 0.88f, size.height * 0.3f)
            }
            drawPath(route, palette.primary.copy(alpha = 0.35f), style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
            listOf(
                Offset(size.width * 0.16f, size.height * 0.7f),
                Offset(size.width * 0.57f, size.height * 0.37f),
                Offset(size.width * 0.88f, size.height * 0.3f),
            ).forEachIndexed { index, point ->
                drawCircle(palette.surface, radius = (if (index == 1) 11 else 8).dp.toPx(), center = point)
                drawCircle(palette.primary, radius = (if (index == 1) 7 else 5).dp.toPx(), center = point)
            }
        }
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 14.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(palette.surface)
                .border(1.dp, palette.border, RoundedCornerShape(12.dp))
                .padding(horizontal = 18.dp, vertical = 9.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "MAPMORY PASSPORT",
                color = palette.primary,
                fontSize = 9.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.3.sp,
            )
            Text(
                "여행의 순간을 지도 위에",
                modifier = Modifier.padding(top = 3.dp),
                color = palette.headingText,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

private val KakaoYellow = Color(0xFFFEE500)
private val KakaoText = Color(0xFF191919)

@Preview(name = "계정 연결, 라이트", showBackground = true, widthDp = 412, heightDp = 900)
@Composable
private fun AccountConnectionLightPreview() {
    ProvideTripRecordPalettes(isDark = false) {
        PreviewSurface { AccountConnectionScreen(onBack = {}) }
    }
}

@Preview(name = "계정 연결, 다크", showBackground = true, widthDp = 412, heightDp = 900)
@Composable
private fun AccountConnectionDarkPreview() {
    ProvideTripRecordPalettes(isDark = true) {
        PreviewSurface { AccountConnectionScreen(onBack = {}) }
    }
}
