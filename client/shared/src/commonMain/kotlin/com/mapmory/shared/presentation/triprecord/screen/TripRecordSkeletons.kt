package com.mapmory.shared.presentation.triprecord.screen

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.mapmory.shared.preview.PreviewSurface

@Composable
internal fun SkeletonBox(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(12.dp),
) {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.55f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "skeletonAlpha",
    )

    Box(
        modifier = modifier
            .clip(shape)
            .background(TripRecordPalette.current.imagePlaceholder.copy(alpha = alpha)),
    )
}

@Composable
internal fun TripRecordListSkeleton(
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 18.dp),
    ) {
        items(listOf(0, 1), key = { it }) {
            SkeletonTripRecordCard()
        }
    }
}

@Composable
private fun SkeletonTripRecordCard() {
    TripRecordCardLayout(
        photo = { TripPhotoPlaceholder(Modifier.fillMaxSize(), RoundedCornerShape(12.dp)) },
        details = {
            SkeletonCardText(11.sp, Modifier.fillMaxWidth(0.65f))
            Spacer(Modifier.height(7.dp))
            SkeletonCardText(19.sp, Modifier.fillMaxWidth(0.5f), FontWeight.Bold)
        },
    )
}

/** 실제 Text와 같은 줄 높이를 측정해 글꼴 배율이 달라도 카드 높이를 맞춘다. */
@Composable
private fun SkeletonCardText(
    fontSize: TextUnit,
    modifier: Modifier = Modifier,
    fontWeight: FontWeight? = null,
) {
    Box(modifier.clearAndSetSemantics {}) {
        Text(" ", fontSize = fontSize, fontWeight = fontWeight, color = Color.Transparent, maxLines = 1)
        SkeletonBox(Modifier.matchParentSize().padding(vertical = 4.dp))
    }
}

/** 목록의 사진 개수로 상세 응답이 오기 전 사진첩 자리를 확보한다. */
@Composable
internal fun TripRecordDetailSkeleton(
    modifier: Modifier = Modifier,
    locationName: String? = null,
    latestDate: String? = null,
    photoCount: Int? = null,
    columnCount: Int = 2,
    onBackClick: () -> Unit = {},
) {
    val visibleLocationName = locationName?.takeIf { it.isNotBlank() } ?: "여행"
    val rows = albumSkeletonRowSizes(photoCount, columnCount)
    Column(modifier.fillMaxSize()) {
        TripRecordTopBar(
            title = "",
            onBackClick = onBackClick,
        )
        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f).navigationBarsPadding(),
            contentPadding = PaddingValues(start = 24.dp, top = 32.dp, end = 32.dp, bottom = 36.dp),
        ) {
            item(key = "heading") { AlbumHeading(locationName = visibleLocationName) }
            if (rows.isNotEmpty()) {
                item(key = "date") {
                    Box(Modifier.padding(top = 32.dp, bottom = 14.dp)) {
                        val date = latestDate.toAlbumDate()
                        if (date != null) {
                            Text(
                                text = date.toAlbumDisplayDate(),
                                color = TripRecordPalette.current.headingText,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                            )
                        } else {
                            SkeletonCardText(18.sp, Modifier.width(140.dp), FontWeight.Bold)
                        }
                    }
                }
                items(rows.size, key = { "row:$it" }) { rowIndex ->
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .padding(top = if (rowIndex == 0) 0.dp else 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        repeat(rows[rowIndex]) {
                            SkeletonBox(
                                Modifier.weight(1f).aspectRatio(1f),
                                RoundedCornerShape(14.dp),
                            )
                        }
                        repeat(columnCount - rows[rowIndex]) {
                            Spacer(Modifier.weight(1f).aspectRatio(1f))
                        }
                    }
                }
            }
        }
    }
}

internal fun albumSkeletonRowSizes(photoCount: Int?, columnCount: Int): List<Int> {
    require(columnCount > 0)
    // 이전 API나 캐시에는 개수가 없으므로 한 장을 기본값으로 사용한다.
    val count = (photoCount ?: 1).coerceAtLeast(0)
    return List(count / columnCount + if (count % columnCount == 0) 0 else 1) { row ->
        minOf(columnCount, count - row * columnCount)
    }
}

@Composable
internal fun TripProfileSkeleton(
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 4.dp, bottom = 18.dp),
        verticalArrangement = Arrangement.spacedBy(15.dp),
    ) {
        item { SkeletonPassportCard() }
        item { SkeletonVisitProgressCard() }
        item { SkeletonRankingCard() }
    }
}

@Composable
private fun SkeletonStatsCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, TripRecordPalette.current.border),
        colors = CardDefaults.cardColors(containerColor = TripRecordPalette.current.surface),
    ) {
        Column(Modifier.padding(21.dp)) { content() }
    }
}

@Composable
private fun SkeletonPassportCard() {
    SkeletonStatsCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SkeletonBox(Modifier.width(112.dp).height(10.dp))
            SkeletonBox(Modifier.width(112.dp).height(14.dp))
        }
        Spacer(Modifier.height(10.dp))
        SkeletonBox(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f),
            shape = RoundedCornerShape(15.dp),
        )
        Row(
            modifier = Modifier.padding(top = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SkeletonBox(Modifier.size(10.dp), RoundedCornerShape(3.dp))
            SkeletonBox(Modifier.width(180.dp).height(10.dp).padding(start = 7.dp))
        }
    }
}

@Composable
private fun SkeletonVisitProgressCard() {
    SkeletonStatsCard {
        SkeletonProgressBlock()
        Spacer(Modifier.height(21.dp))
        Spacer(Modifier.fillMaxWidth().height(1.dp).background(TripStatisticsPalette.current.divider))
        Spacer(Modifier.height(18.dp))
        SkeletonProgressBlock()
        Spacer(Modifier.height(20.dp))
        Spacer(Modifier.fillMaxWidth().height(1.dp).background(TripStatisticsPalette.current.divider))
        Row(Modifier.fillMaxWidth().padding(top = 17.dp)) {
            SkeletonSummaryValue(Modifier.weight(1f))
            SkeletonSummaryValue(Modifier.weight(1f))
        }
    }
}

@Composable
private fun SkeletonProgressBlock() {
    SkeletonBox(Modifier.width(118.dp).height(14.dp))
    SkeletonBox(Modifier.width(68.dp).height(30.dp).padding(top = 9.dp))
    SkeletonBox(
        modifier = Modifier
            .fillMaxWidth()
            .height(10.dp)
            .padding(top = 12.dp),
        shape = CircleShape,
    )
}

@Composable
private fun SkeletonSummaryValue(modifier: Modifier = Modifier) {
    Column(modifier) {
        SkeletonBox(Modifier.width(52.dp).height(11.dp))
        SkeletonBox(Modifier.width(34.dp).height(20.dp).padding(top = 4.dp))
    }
}

@Composable
private fun SkeletonRankingCard() {
    SkeletonStatsCard {
        SkeletonBox(Modifier.width(132.dp).height(16.dp))
        Column(
            modifier = Modifier.padding(top = 17.dp),
            verticalArrangement = Arrangement.spacedBy(13.dp),
        ) {
            repeat(3) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SkeletonBox(Modifier.size(26.dp), CircleShape)
                    SkeletonBox(
                        modifier = Modifier
                            .width(120.dp)
                            .height(13.dp)
                            .padding(start = 10.dp),
                    )
                    Spacer(Modifier.weight(1f))
                    SkeletonBox(Modifier.width(28.dp).height(12.dp))
                }
            }
        }
    }
}

@Preview(
    name = "기록 목록 스켈레톤",
    showBackground = true,
    widthDp = 412,
    heightDp = 900,
)
@Composable
internal fun TripRecordListSkeletonPreview() {
    PreviewSurface {
        TripRecordListSkeleton(modifier = Modifier.fillMaxSize())
    }
}

@Preview(
    name = "통계 스켈레톤",
    showBackground = true,
    widthDp = 412,
    heightDp = 900,
)
@Composable
internal fun TripProfileSkeletonPreview() {
    PreviewSurface {
        TripProfileSkeleton(modifier = Modifier.fillMaxSize())
    }
}

@Preview(
    name = "기록 상세 스켈레톤",
    showBackground = true,
    widthDp = 412,
    heightDp = 900,
)
@Composable
internal fun TripRecordDetailSkeletonPreview() {
    PreviewSurface {
        TripRecordDetailSkeleton(modifier = Modifier.fillMaxSize())
    }
}
