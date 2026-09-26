package com.mapmory.shared.presentation.triprecord.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun FirstRecordIntroduction(
    onAddRecord: () -> Unit,
    onExplore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = TripRecordPalette.current
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(palette.mediaScrim),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(palette.surface, RoundedCornerShape(24.dp))
                    .border(1.dp, palette.border, RoundedCornerShape(24.dp))
                    .padding(horizontal = 24.dp, vertical = 28.dp),
            ) {
                Text(
                    text = "PHOTO MAP",
                    color = palette.accent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                )
                Text(
                    text = "사진으로 여행 지도를\n만들어요",
                    color = palette.headingText,
                    fontSize = 23.sp,
                    lineHeight = 32.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 18.dp),
                )
                Text(
                    text = "장소와 날짜를 고르면 사진첩에서 여행 사진을 찾아드려요.",
                    color = palette.bodyText,
                    fontSize = 14.sp,
                    lineHeight = 21.sp,
                    modifier = Modifier.padding(top = 14.dp),
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 26.dp)
                        .height(56.dp)
                        .background(palette.primary, RoundedCornerShape(16.dp))
                        .semantics { contentDescription = "여행 기록 추가하기" }
                        .clickable(role = Role.Button, onClick = onAddRecord),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "여행 기록 추가하기",
                        color = palette.onPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
            Text(
                text = "추가하지 않고 둘러보기",
                color = palette.contentOnMedia,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "추가하지 않고 둘러보기" }
                    .clickable(role = Role.Button, onClick = onExplore)
                    .padding(vertical = 12.dp),
            )
        }
    }
}
