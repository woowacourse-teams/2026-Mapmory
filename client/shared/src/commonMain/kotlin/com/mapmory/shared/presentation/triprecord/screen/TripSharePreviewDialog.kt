package com.mapmory.shared.presentation.triprecord.screen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import com.mapmory.shared.presentation.triprecord.share.rememberShareImageExporter
import kotlinx.coroutines.CancellationException
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.mapmory.shared.presentation.components.MapmoryAsyncImage
import com.mapmory.shared.presentation.triprecord.state.TripRecordItemUiState
import com.mapmory.shared.presentation.triprecord.state.TripRecordPhotoUiState
import com.mapmory.shared.presentation.triprecord.state.TripStatisticsUiModel
import com.mapmory.shared.presentation.triprecord.state.localOriginalUri
import kotlinx.coroutines.launch
import mapmoryclient.shared.generated.resources.Res
import mapmoryclient.shared.generated.resources.share_outlined_24px
import mapmoryclient.shared.generated.resources.download_outlined_24px
import mapmoryclient.shared.generated.resources.close_outlined_24px
import org.jetbrains.compose.resources.painterResource

private val EditorialInk = Color(0xFF192720)
private val EditorialGreen = Color(0xFF4D9272)
private val TicketPaper = Color(0xFFF8F7F3)
private val ShareBackdrop = Color(0xFF15231F)

@Composable
internal fun TripSharePreviewButton(
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = TripRecordPalette.current
    Box(
        modifier.size(48.dp).clip(RoundedCornerShape(16.dp))
            .semantics { this.contentDescription = contentDescription }
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(40.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(colors.primarySoft),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(Res.drawable.share_outlined_24px), null,
                tint = colors.primary, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
internal fun TripRecordSharePreviewDialog(
    record: TripRecordItemUiState,
    onDismiss: () -> Unit,
) {
    val pageTitles = listOf("여행 매거진", "여행 엽서", "여행 티켓", "사진 에디토리얼", "사진 위 여행 정보")
    SharePreviewDialog(
        title = "여행 기록 공유",
        pageTitles = pageTitles,
        onDismiss = onDismiss,
    ) { page, modifier ->
        TripRecordSharePoster(record, page, modifier)
    }
}

/** The preview and exported image use the same composition. */
@Composable
internal fun TripRecordSharePoster(record: TripRecordItemUiState, page: Int, modifier: Modifier) {
    when (page) {
        0 -> DestinationEditionPoster(record, modifier)
        1 -> PostcardPoster(record, modifier)
        2 -> TravelTicketPoster(record, modifier)
        3 -> PhotoEditorialPoster(record, modifier)
        else -> PhotoOverlayPoster(record, modifier)
    }
}

@Composable
internal fun TripCollectionSharePreviewDialog(
    records: List<TripRecordItemUiState>,
    onDismiss: () -> Unit,
) {
    SharePreviewDialog(
        title = "불러온 여행 모음 공유",
        pageTitles = listOf("사진 콘택트 시트"),
        supportingText = "현재 목록의 기록 ${records.size}개에서 대표 사진을 담아요",
        pageTitleAbovePreview = true,
        onDismiss = onDismiss,
    ) { _, modifier ->
        ContactSheetPoster(records, modifier)
    }
}

@Composable
internal fun TripArchiveSharePreviewDialog(
    statistics: TripStatisticsUiModel,
    onDismiss: () -> Unit,
) {
    SharePreviewDialog(
        title = "나의 여행 공유",
        pageTitles = listOf("나의 여행 통계"),
        onDismiss = onDismiss,
    ) { _, modifier ->
        ArchivePoster(statistics, modifier)
    }
}

private class PosterCapture {
    var layer: GraphicsLayer? = null
    var drawn = false
    val photos = mutableStateMapOf<String, Boolean>()
}

private val LocalPosterCapture = staticCompositionLocalOf<PosterCapture?> { null }

@Composable
private fun SharePreviewDialog(
    title: String,
    pageTitles: List<String>,
    supportingText: String = "내 여행에 어울리는 디자인을 골라보세요",
    pageTitleAbovePreview: Boolean = false,
    onDismiss: () -> Unit,
    poster: @Composable (Int, Modifier) -> Unit,
) {
    val pagerState = rememberPagerState(pageCount = { pageTitles.size })
    val scope = rememberCoroutineScope()
    val exporter = rememberShareImageExporter()
    val captures = remember { mutableMapOf<Int, PosterCapture>() }
    var exporting by remember { mutableStateOf(false) }
    var feedback by remember { mutableStateOf<String?>(null) }
    fun export(save: Boolean) {
        if (exporting || pagerState.isScrollInProgress) return
        val capture = captures[pagerState.currentPage]
        if (capture == null || !capture.drawn || capture.photos.values.any { !it }) {
            feedback = "사진을 불러오는 중이에요. 잠시 후 다시 눌러주세요."
            return
        }
        val layer = capture.layer ?: return
        exporting = true
        feedback = null
        scope.launch {
            try {
                withFrameNanos { }
                val image = layer.toImageBitmap()
                if (save) exporter.save(image) else exporter.share(image)
                feedback = if (save) "사진 앱에 저장했어요." else null
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                feedback = error.message ?: "이미지를 내보내지 못했어요. 다시 시도해 주세요."
            } finally {
                exporting = false
            }
        }
    }

    Dialog(
        onDismissRequest = { if (!exporting) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            Modifier.fillMaxSize()
                .background(ShareBackdrop)
                .padding(top = 8.dp, bottom = 20.dp),
        ) {
            Row(
                Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                if (pageTitleAbovePreview) {
                    Column(verticalArrangement = Arrangement.Center) {
                        Text(title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text(
                            pageTitles[pagerState.currentPage],
                            color = Color(0xFFAABDB1),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                } else {
                    Text(title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
                IconButton(onClick = onDismiss, enabled = !exporting) {
                    Box(
                        Modifier.size(36.dp).background(Color(0xFF2A3C32), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(painterResource(Res.drawable.close_outlined_24px), "공유 화면 닫기",
                            tint = Color(0xFFD2DFD7), modifier = Modifier.size(18.dp))
                    }
                }
            }
            if (pageTitles.size > 1) {
                Text(
                    text = supportingText,
                    color = Color(0xFFAABDB1),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(start = 22.dp, bottom = 8.dp),
                )
            } else {
                Spacer(Modifier.height(8.dp))
            }
            HorizontalPager(
                state = pagerState,
                contentPadding = PaddingValues(horizontal = 28.dp),
                pageSpacing = 16.dp,
                userScrollEnabled = !exporting,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) { page ->
                val capture = remember { PosterCapture() }
                DisposableEffect(page, capture) {
                    captures[page] = capture
                    onDispose { captures.remove(page) }
                }
                CompositionLocalProvider(LocalPosterCapture provides capture) {
                    poster(page, Modifier.fillMaxSize())
                }
            }
            if (!pageTitleAbovePreview) {
                Text(
                    pageTitles[pagerState.currentPage],
                    color = Color(0xFFE5EDE8),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 12.dp),
                )
            }
            if (pageTitles.size > 1) {
                SharePageIndicators(
                    pagerState = pagerState,
                    pageTitles = pageTitles,
                    onSelect = { page -> if (!exporting) scope.launch { pagerState.animateScrollToPage(page) } },
                )
            }
            feedback?.let { message ->
                Text(
                    message,
                    color = Color(0xFFE5EDE8),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(
                    onClick = { export(save = true) },
                    enabled = !exporting && !pagerState.isScrollInProgress,
                    modifier = Modifier.weight(1f).height(54.dp),
                    shape = RoundedCornerShape(16.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFE9F2ED), contentColor = Color(0xFF294F3C),
                    ),
                ) {
                    Icon(painterResource(Res.drawable.download_outlined_24px), null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (exporting) "처리 중…" else "이미지 저장", fontWeight = FontWeight.SemiBold)
                }
                Button(
                    onClick = { export(save = false) },
                    enabled = !exporting && !pagerState.isScrollInProgress,
                    modifier = Modifier.weight(1f).height(54.dp),
                    shape = RoundedCornerShape(16.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = EditorialGreen, contentColor = Color.White,
                    ),
                ) {
                    Icon(painterResource(Res.drawable.share_outlined_24px), null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("공유하기", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun SharePageIndicators(
    pagerState: PagerState,
    pageTitles: List<String>,
    onSelect: (Int) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 14.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        pageTitles.forEachIndexed { index, pageTitle ->
            val selected = pagerState.currentPage == index
            Box(
                Modifier.size(width = minOf(40, 280 / pageTitles.size).dp, height = 40.dp)
                    .clip(CircleShape)
                    .selectable(
                        selected = selected,
                        role = Role.Tab,
                        onClick = { onSelect(index) },
                    )
                    .semantics {
                        this.selected = selected
                        contentDescription = "${index + 1}번 시안, $pageTitle"
                    },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .width(if (selected) 20.dp else 7.dp)
                        .height(7.dp)
                        .clip(CircleShape)
                        .background(
                            if (selected) Color(0xFF9CC5AB)
                            else Color(0xFF53665A),
                        ),
                )
            }
        }
    }
}

@Composable
internal fun PosterFrame(
    modifier: Modifier,
    content: @Composable Modifier.() -> Unit,
) {
    val capture = LocalPosterCapture.current
    val layer = rememberGraphicsLayer()
    SideEffect { capture?.layer = layer }
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val posterWidth = minOf(maxWidth, maxHeight * (4f / 5f))
        val density = LocalDensity.current
        // Keep the reference's 360 x 450 composition intact on every preview size.
        val posterDensity = Density(density.density * posterWidth.value / 360f, fontScale = 1f)
        Box(
            Modifier.width(posterWidth)
                .aspectRatio(4f / 5f)
                .clip(RoundedCornerShape(3.dp))
                .drawWithContent {
                    layer.record { this@drawWithContent.drawContent() }
                    drawLayer(layer)
                    capture?.drawn = true
                },
        ) {
            CompositionLocalProvider(LocalDensity provides posterDensity) {
                ProvideTextStyle(LocalTextStyle.current.copy(lineHeight = 16.sp)) {
                    content(Modifier.fillMaxSize())
                }
            }
        }
    }
}

@Composable
private fun PhotoEditorialPoster(record: TripRecordItemUiState, modifier: Modifier) {
    PosterFrame(modifier) {
        Column(Modifier.fillMaxSize().background(TicketPaper).padding(24.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Text("mapmory", color = EditorialInk, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text("여행 기록", color = Color(0xFF65736B), fontSize = 9.sp)
            }
            Spacer(Modifier.height(22.dp))
            SingleRecordPhoto(record, Modifier.fillMaxWidth().weight(1f))
            Text(record.locationName, color = EditorialInk, fontSize = 29.sp, lineHeight = 34.sp,
                fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 19.dp))
            ShareRecordCaption(record, EditorialInk, Modifier.padding(top = 6.dp))
            Spacer(Modifier.height(14.dp))
            ShareRecordFooter(record, Color(0xFF58665E))
        }
    }
}

@Composable
private fun TravelTicketPoster(record: TripRecordItemUiState, modifier: Modifier) {
    PosterFrame(modifier) {
        Column(Modifier.fillMaxSize().background(TicketPaper)) {
            Row(Modifier.fillMaxWidth().background(EditorialInk).padding(horizontal = 24.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("mapmory", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text("TRAVEL TICKET", color = Color(0xFFD7E2DA), fontSize = 8.sp, letterSpacing = 1.sp)
            }
            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
                Text("도착한 곳", color = Color(0xFF65736B), fontSize = 9.sp, modifier = Modifier.padding(top = 20.dp))
                Text(record.locationName, color = EditorialInk,
                    fontSize = if (record.locationName.length > 10) 30.sp else 36.sp, lineHeight = 38.sp,
                    fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 5.dp, bottom = 18.dp))
            }
            SingleRecordPhoto(record, Modifier.padding(horizontal = 24.dp).fillMaxWidth().weight(1f))
            DashedDivider(Modifier.fillMaxWidth().padding(top = 22.dp, bottom = 18.dp))
            Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                TicketMetadata("여행 날짜", recordDateRange(record), Modifier.weight(1f))
                TicketMetadata("남긴 사진", "${record.photoCount ?: record.photos.size}장", Modifier.width(58.dp))
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(22.dp).height(2.dp).background(EditorialGreen))
                Text("나의 여행 기록", color = Color(0xFF65736B), fontSize = 9.sp)
            }
        }
    }
}

@Composable
private fun PhotoOverlayPoster(record: TripRecordItemUiState, modifier: Modifier) {
    PosterFrame(modifier) {
        Box(Modifier.fillMaxSize().background(EditorialInk)) {
            SingleRecordPhoto(record, Modifier.fillMaxSize())
            // Shade only the caption area so the photograph keeps its original colors.
            Box(Modifier.fillMaxWidth().height(280.dp).align(Alignment.BottomCenter)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.78f)))))
            Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(26.dp)) {
                Text(record.locationName, color = Color.White, fontSize = 34.sp, lineHeight = 40.sp,
                    fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                ShareRecordCaption(record, Color.White.copy(alpha = 0.9f), Modifier.padding(top = 8.dp))
                Text(recordDateRange(record), color = Color.White.copy(alpha = 0.88f), fontSize = 10.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 12.dp))
                Row(Modifier.fillMaxWidth().padding(top = 23.dp), horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("mapmory", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text("사진 ${record.photoCount ?: record.photos.size}장", color = Color.White.copy(alpha = 0.85f), fontSize = 9.sp)
                }
            }
        }
    }
}

@Composable
private fun SingleRecordPhoto(record: TripRecordItemUiState, modifier: Modifier) {
    val photo = record.photos.minByOrNull(TripRecordPhotoUiState::sortOrder)
    if (photo == null) PosterPhotoPlaceholder(modifier, "사진 없이 남긴 기록")
    else SharePosterPhoto(photo, modifier, "${record.locationName} 여행 사진")
}

@Composable
private fun ShareRecordCaption(record: TripRecordItemUiState, color: Color, modifier: Modifier) {
    val caption = record.title.trim().takeIf { it.isNotEmpty() && it != record.locationName.trim() }
    if (caption != null) {
        Text(caption, color = color, fontSize = 12.sp, lineHeight = 18.sp,
            maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = modifier)
    }
}

@Composable
private fun ShareRecordFooter(record: TripRecordItemUiState, color: Color) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Bottom) {
        Text(recordDateRange(record), color = color, fontSize = 9.sp, maxLines = 1,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Text("사진 ${record.photoCount ?: record.photos.size}장", color = color, fontSize = 9.sp)
    }
}

@Composable
private fun ArchivePoster(statistics: TripStatisticsUiModel, modifier: Modifier) {
    PosterFrame(modifier) {
        Column(Modifier.fillMaxSize().background(Color(0xFFFBFBF8)).padding(horizontal = 26.dp, vertical = 26.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("MAPMORY", color = Color(0xFF75897D), fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                Text("MY TRAVEL DATA", color = Color(0xFF849087), fontSize = 8.sp, letterSpacing = 1.sp)
            }
            Text("여행이 쌓인 만큼", color = EditorialInk, fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp))
            Text("내가 남긴 기록을 숫자로 돌아봐요", color = Color(0xFF829087), fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
            Box(Modifier.padding(top = 12.dp).fillMaxWidth().height(1.dp).background(Color(0xFFDCE4DE)))
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ArchiveMetric(statistics.recordCount.toString().padStart(2, '0'), "여행 기록", Modifier.weight(1f))
                Box(Modifier.width(1.dp).height(57.dp).background(Color(0xFFDCE4DE)))
                ArchiveMetric(statistics.photoCount.toString().padStart(2, '0'), "사진", Modifier.weight(1f).padding(start = 18.dp))
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFDCE4DE)))
            Text("자주 기록한 장소", color = Color(0xFF77857D), fontSize = 10.sp, modifier = Modifier.padding(top = 12.dp, bottom = 6.dp))
            if (statistics.topLocations.isEmpty()) {
                Text("기록한 장소가 생기면 여기에 표시돼요", color = Color(0xFF87938A), fontSize = 11.sp)
            } else {
                val locations = statistics.topLocations.take(3)
                val maxVisitCount = locations.maxOf { it.visitCount }.coerceAtLeast(1)
                locations.forEach { location ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(location.locationName, color = EditorialInk, fontSize = 11.sp, modifier = Modifier.width(54.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Box(Modifier.weight(1f).height(4.dp).clip(CircleShape).background(Color(0xFFE3E9E4))) {
                            Box(Modifier.fillMaxWidth(location.visitCount.toFloat() / maxVisitCount).height(4.dp).background(Color(0xFF7BA88F), CircleShape))
                        }
                        Text("${location.visitCount}회", color = Color(0xFF7C8981), fontSize = 9.sp)
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFDCE4DE)))
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${statistics.travelerName}의 여행 기록", color = Color(0xFF839188), fontSize = 10.sp)
                Text("mapmory", color = EditorialInk, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun DashedDivider(modifier: Modifier) {
    Canvas(modifier.height(1.dp)) {
        drawLine(
            color = Color(0xFFB8C1B9),
            start = Offset.Zero.copy(y = size.height / 2),
            end = Offset(size.width, size.height / 2),
            strokeWidth = 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx())),
        )
    }
}

@Composable
private fun TicketMetadata(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, color = Color(0xFF65736B), fontSize = 9.sp)
        Text(value, color = EditorialInk, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ArchiveMetric(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(value, color = EditorialGreen, fontSize = 43.sp, fontWeight = FontWeight.Light, lineHeight = 47.sp)
        Text(label, color = Color(0xFF75837A), fontSize = 10.sp)
    }
}

@Composable
internal fun SharePosterPhoto(
    photo: TripRecordPhotoUiState,
    modifier: Modifier,
    description: String,
    shape: RoundedCornerShape = RoundedCornerShape(0.dp),
) {
    val capture = LocalPosterCapture.current
    DisposableEffect(capture, photo.id) {
        capture?.photos?.set(photo.id, false)
        onDispose { capture?.photos?.remove(photo.id) }
    }
    MapmoryAsyncImage(
        imageBytes = photo.originalBytes?.bytesForDecoding() ?: photo.previewBytes?.bytesForDecoding(),
        imageUri = photo.fullResolutionUri ?: photo.localOriginalUri ?: photo.previewUri,
        fallbackBytes = photo.previewBytes?.bytesForDecoding(),
        fallbackUri = photo.previewUri,
        cacheKey = "share-poster:${photo.id}",
        onReadyChanged = { ready -> capture?.photos?.set(photo.id, ready) },
        contentDescription = description,
        modifier = modifier.clip(shape),
        shape = shape,
    )
}

@Composable
internal fun PosterPhotoPlaceholder(modifier: Modifier, locationName: String) {
    Box(
        modifier.background(Color(0xFFE2EAE4)),
        contentAlignment = Alignment.Center,
    ) {
        Text(locationName, color = Color(0xFF78877E), fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
