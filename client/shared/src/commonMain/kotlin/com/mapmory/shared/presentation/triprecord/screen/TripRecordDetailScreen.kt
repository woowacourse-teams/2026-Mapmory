package com.mapmory.shared.presentation.triprecord.screen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mapmory.shared.presentation.triprecord.state.TripRecordDetailUiState
import com.mapmory.shared.presentation.triprecord.state.TripRecordItemUiState
import com.mapmory.shared.preview.PreviewSurface
import com.mapmory.shared.preview.previewUiRecords

@Composable
fun TripRecordDetailScreen(
    uiState: TripRecordDetailUiState,
    onBackClick: () -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onMapClick: () -> Unit = {},
    onRecordClick: () -> Unit = {},
    onCreateClick: () -> Unit = {},
    onProfileClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var showDeleteDialog by remember { mutableStateOf(false) }

    TripRecordBackground(modifier = modifier) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f)) {
                when (uiState) {
                    TripRecordDetailUiState.Idle,
                    TripRecordDetailUiState.Loading,
                    TripRecordDetailUiState.Deleting,
                    -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = TripRecordPalette.current.accent)
                    }

                    is TripRecordDetailUiState.Error -> Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(uiState.message, color = TripRecordPalette.current.danger)
                        TextButton(onClick = onBackClick) { Text("목록으로") }
                    }

                    is TripRecordDetailUiState.Success -> {
                        val record = uiState.record
                        TripRecordDetailContent(
                            record = record,
                            onBackClick = onBackClick,
                            onEditClick = onEditClick,
                            onDeleteClick = { showDeleteDialog = true },
                        )
                    }
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("여행 기록 삭제") },
            text = { Text("삭제한 기록은 복구할 수 없습니다.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        onDeleteClick()
                    },
                ) {
                    Text("삭제", color = TripRecordPalette.current.danger)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("취소") }
            },
        )
    }
}

@Composable
private fun TripRecordDetailContent(
    record: TripRecordItemUiState,
    onBackClick: () -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        TripRecordPhotoSection(
            record = record,
            onBackClick = onBackClick,
            onEditClick = onEditClick,
            onDeleteClick = onDeleteClick,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        )
        TripRecordBottomCard(
            record = record,
            modifier = Modifier
                .overlapPhoto(24.dp)
                .fillMaxWidth()
                .fillMaxHeight(0.4f),
        )
    }
}

@Composable
private fun TripRecordPhotoSection(
    record: TripRecordItemUiState,
    onBackClick: () -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val media = remember(record.id, record.photos) {
        record.photos.sortedBy { it.sortOrder }
    }

    Box(modifier = modifier) {
        if (media.isEmpty()) {
            TripPhotoImage(
                imageBytes = null,
                contentDescription = record.title,
                modifier = Modifier.fillMaxSize(),
                placeholderVariant = record.id.toInt() + 1,
                shape = RectangleShape,
            )
        } else {
            val pagerState = rememberPagerState(pageCount = { media.size })
            LaunchedEffect(record.id) {
                pagerState.scrollToPage(0)
            }
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                val photo = media[page]
                TripPhotoImage(
                    // Preview bytes are orientation-normalized display data. Original bytes stay
                    // untouched for persistence/upload and may carry EXIF orientation that the
                    // common Skia decoder does not apply consistently across platforms.
                    imageBytes = photo.previewBytes?.bytesForDecoding()
                        ?: photo.originalBytes?.bytesForDecoding(),
                    fallbackBytes = photo.originalBytes?.bytesForDecoding(),
                    contentDescription = "${record.title} 사진 ${page + 1}",
                    modifier = Modifier.fillMaxSize(),
                    placeholderVariant = record.id.toInt() + page + 1,
                    shape = RectangleShape,
                )
            }
            if (media.size > 1) {
                PhotoPageIndicator(
                    pageCount = media.size,
                    currentPage = pagerState.currentPage,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 24.dp),
                )
            }
        }

        DetailTopActions(
            onBackClick = onBackClick,
            onEditClick = onEditClick,
            onDeleteClick = onDeleteClick,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding(),
        )
    }
}

@Composable
private fun PhotoPageIndicator(
    pageCount: Int,
    currentPage: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
            .background(
                color = TripRecordPalette.current.background.copy(alpha = 0.72f),
                shape = RoundedCornerShape(12.dp),
            )
            .padding(horizontal = 9.dp, vertical = 7.dp),
    ) {
        repeat(pageCount) { page ->
            Box(
                modifier = Modifier
                    .size(if (page == currentPage) 7.dp else 5.dp)
                    .background(
                        color = if (page == currentPage) {
                            TripRecordPalette.current.accent
                        } else {
                            TripRecordPalette.current.muted.copy(alpha = 0.7f)
                        },
                        shape = CircleShape,
                    ),
            )
        }
    }
}

@Composable
private fun DetailTopActions(
    onBackClick: () -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 20.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        DetailBackButton(onClick = onBackClick)
        DetailMoreButton(
            onEditClick = onEditClick,
            onDeleteClick = onDeleteClick,
        )
    }
}

@Composable
private fun DetailBackButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TripIconButton(
        label = "←",
        contentDescription = "뒤로가기",
        onClick = onClick,
        containerColor = TripRecordPalette.current.surface.copy(alpha = 0.5f),
        contentColor = TripRecordPalette.current.contentOnMedia,
        modifier = modifier,
    )
}

@Composable
private fun DetailMoreButton(
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        TripIconButton(
            label = "•••",
            contentDescription = "더보기",
            onClick = { expanded = true },
            containerColor = TripRecordPalette.current.surface.copy(alpha = 0.5f),
            contentColor = TripRecordPalette.current.contentOnMedia,
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DropdownMenuItem(
                text = { Text("수정") },
                onClick = {
                    expanded = false
                    onEditClick()
                },
            )
            DropdownMenuItem(
                text = { Text("삭제", color = TripRecordPalette.current.danger) },
                onClick = {
                    expanded = false
                    onDeleteClick()
                },
            )
        }
    }
}

@Composable
private fun TripRecordBottomCard(
    record: TripRecordItemUiState,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    LaunchedEffect(record.id) {
        scrollState.scrollTo(0)
    }

    Column(
        modifier = modifier
            .background(
                color = TripRecordPalette.current.surface,
                shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
            )
            .verticalScroll(scrollState)
            .padding(horizontal = 24.dp, vertical = 24.dp),
    ) {
        val dateRange = record.formattedDateRange()

        Row(
            modifier = Modifier.padding(bottom = 17.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RecordMetadataChip(
                text = record.locationName,
                icon = RecordMetadataIcon.Location,
                containerColor = TripRecordPalette.current.accentSoft,
                contentColor = TripRecordPalette.current.accent,
                modifier = Modifier.weight(weight = 1f, fill = false),
            )
            dateRange?.let {
                RecordMetadataChip(
                    text = it,
                    icon = RecordMetadataIcon.Date,
                    containerColor = TripRecordPalette.current.metadataDateBackground,
                    contentColor = TripRecordPalette.current.text,
                )
            }
        }
        Text(
            text = record.title,
            color = TripRecordPalette.current.text,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = record.content,
            color = TripRecordPalette.current.muted,
            fontSize = 14.sp,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun RecordMetadataChip(
    text: String,
    icon: RecordMetadataIcon,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .background(containerColor, RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RecordMetadataIcon(
            icon = icon,
            color = contentColor,
            modifier = Modifier.size(10.dp),
        )
        Text(
            text = text,
            color = contentColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun RecordMetadataIcon(
    icon: RecordMetadataIcon,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val strokeWidth = 1.25.dp.toPx()
        val center = Offset(size.width / 2f, size.height / 2f)

        when (icon) {
            RecordMetadataIcon.Location -> {
                val radius = size.minDimension * 0.27f
                drawCircle(
                    color = color,
                    radius = radius,
                    center = center,
                    style = Stroke(width = strokeWidth),
                )
                drawCircle(color = color, radius = strokeWidth * 0.7f, center = center)
                val markerGap = radius * 0.75f
                drawLine(color, Offset(center.x, 0f), Offset(center.x, markerGap), strokeWidth)
                drawLine(
                    color,
                    Offset(center.x, size.height - markerGap),
                    Offset(center.x, size.height),
                    strokeWidth,
                )
                drawLine(color, Offset(0f, center.y), Offset(markerGap, center.y), strokeWidth)
                drawLine(
                    color,
                    Offset(size.width - markerGap, center.y),
                    Offset(size.width, center.y),
                    strokeWidth,
                )
            }

            RecordMetadataIcon.Date -> {
                val radius = size.minDimension * 0.37f
                drawCircle(
                    color = color,
                    radius = radius,
                    center = center,
                    style = Stroke(width = strokeWidth),
                )
                drawLine(
                    color = color,
                    start = center,
                    end = Offset(center.x, center.y - radius * 0.55f),
                    strokeWidth = strokeWidth,
                )
                drawLine(
                    color = color,
                    start = center,
                    end = Offset(center.x + radius * 0.5f, center.y),
                    strokeWidth = strokeWidth,
                )
            }
        }
    }
}

private fun TripRecordItemUiState.formattedDateRange(): String? {
    val formattedStartDate = startDate?.toMetadataDate()
    val formattedEndDate = endDate?.toMetadataDate()

    return when {
        formattedStartDate == null -> formattedEndDate
        formattedEndDate == null || formattedStartDate == formattedEndDate -> formattedStartDate
        else -> "$formattedStartDate – $formattedEndDate"
    }
}

private fun String.toMetadataDate(): String {
    val dateParts = split('-')
    return if (dateParts.size == 3) dateParts.joinToString(". ") else replace('-', '.')
}

private enum class RecordMetadataIcon {
    Location,
    Date,
}

@Preview(
    name = "여행 기록 상세",
    showBackground = true,
    widthDp = 412,
    heightDp = 900,
)
@Composable
fun TripRecordDetailScreenPreview() {
    PreviewSurface {
        TripRecordDetailScreen(
            uiState = TripRecordDetailUiState.Success(previewUiRecords.first()),
            onBackClick = {},
            onEditClick = {},
            onDeleteClick = {},
        )
    }
}

@Preview(
    name = "여행 기록 상세 로딩",
    showBackground = true,
    widthDp = 412,
    heightDp = 900,
)
@Composable
fun LoadingTripRecordDetailScreenPreview() {
    PreviewSurface {
        TripRecordDetailScreen(
            uiState = TripRecordDetailUiState.Loading,
            onBackClick = {},
            onEditClick = {},
            onDeleteClick = {},
        )
    }
}

@Preview(
    name = "여행 기록 상세 오류",
    showBackground = true,
    widthDp = 412,
    heightDp = 900,
)
@Composable
fun ErrorTripRecordDetailScreenPreview() {
    PreviewSurface {
        TripRecordDetailScreen(
            uiState = TripRecordDetailUiState.Error("여행 기록을 불러오지 못했어요."),
            onBackClick = {},
            onEditClick = {},
            onDeleteClick = {},
        )
    }
}


private fun Modifier.overlapPhoto(overlap: Dp): Modifier = layout { measurable, constraints ->
    val overlapPx = overlap.roundToPx()
    val placeable = measurable.measure(constraints)
    layout(
        width = placeable.width,
        height = (placeable.height - overlapPx).coerceAtLeast(0),
    ) {
        placeable.placeRelative(x = 0, y = -overlapPx)
    }
}
