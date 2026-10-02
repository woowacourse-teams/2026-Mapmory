package com.mapmory.shared.presentation.triprecord.screen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mapmory.shared.presentation.triprecord.state.TripRecordDetailUiState
import com.mapmory.shared.presentation.triprecord.state.TripRecordItemUiState
import com.mapmory.shared.presentation.triprecord.state.TripRecordPhotoUiState
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
    onInternalBackHandlerChanged: ((() -> Boolean)?) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    var expandedPhotoIndex by remember { mutableStateOf<Int?>(null) }
    val latestPhotoViewerBackHandler by rememberUpdatedState {
        expandedPhotoIndex = null
        true
    }

    DisposableEffect(expandedPhotoIndex) {
        onInternalBackHandlerChanged(
            if (expandedPhotoIndex == null) null else latestPhotoViewerBackHandler,
        )
        onDispose { onInternalBackHandlerChanged(null) }
    }

    TripRecordBackground(modifier = modifier) {
        when (uiState) {
            TripRecordDetailUiState.Idle,
            TripRecordDetailUiState.Loading,
            -> TripRecordDetailSkeleton(Modifier.fillMaxSize())

            TripRecordDetailUiState.Deleting -> Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
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
                val groups = remember(record.id, record.photos, record.startDate) {
                    groupTripRecordPhotosByDate(record.photos, record.startDate)
                }
                val orderedPhotos = remember(groups) { groups.flatMap(TripRecordPhotoGroup::photos) }
                val selectedIndex = expandedPhotoIndex

                if (selectedIndex != null && orderedPhotos.isNotEmpty()) {
                    ExpandedTripPhotoViewer(
                        locationName = record.locationName,
                        photos = orderedPhotos,
                        initialPage = selectedIndex.coerceIn(orderedPhotos.indices),
                        onBackClick = { expandedPhotoIndex = null },
                    )
                } else {
                    TripRecordPhotoAlbum(
                        record = record,
                        groups = groups,
                        onBackClick = onBackClick,
                        onEditClick = onEditClick,
                        onDeleteClick = { showDeleteDialog = true },
                        onPhotoClick = { photo ->
                            expandedPhotoIndex = orderedPhotos.indexOfFirst { it.id == photo.id }
                                .takeIf { it >= 0 }
                        },
                    )
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
private fun TripRecordPhotoAlbum(
    record: TripRecordItemUiState,
    groups: List<TripRecordPhotoGroup>,
    onBackClick: () -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onPhotoClick: (TripRecordPhotoUiState) -> Unit,
) {
    val scrollState = rememberScrollState()
    LaunchedEffect(record.id) { scrollState.scrollTo(0) }

    Column(Modifier.fillMaxSize()) {
        TripRecordTopBar(
            title = record.locationName,
            onBackClick = onBackClick,
            trailing = {
                DetailMoreButton(
                    onEditClick = onEditClick,
                    onDeleteClick = onDeleteClick,
                )
            },
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(TripRecordPalette.current.line),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .navigationBarsPadding()
                    .padding(start = 24.dp, top = 32.dp, end = 32.dp, bottom = 36.dp),
            ) {
                AlbumHeading(
                    locationName = record.locationName,
                    photoCount = record.photos.size,
                )
                if (groups.isEmpty()) {
                    EmptyPhotoAlbum()
                } else {
                    groups.forEachIndexed { index, group ->
                        PhotoDateGroup(
                            group = group,
                            onPhotoClick = onPhotoClick,
                            modifier = Modifier.padding(top = if (index == 0) 32.dp else 34.dp),
                        )
                    }
                }
            }
            AlbumScrollBar(
                scrollValue = scrollState.value,
                maxScrollValue = scrollState.maxValue,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 5.dp, top = 8.dp, bottom = 8.dp),
            )
        }
    }
}

@Composable
private fun AlbumHeading(
    locationName: String,
    photoCount: Int,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "PHOTO LIBRARY",
                color = TripRecordPalette.current.accent,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
            )
            Text(
                text = "$locationName 사진첩",
                color = TripRecordPalette.current.headingText,
                fontSize = 28.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 16.dp),
            )
            Text(
                text = "날짜별로 모아둔 여행 사진이에요.",
                color = TripRecordPalette.current.secondaryText,
                fontSize = 14.sp,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
        Column(
            modifier = Modifier
                .padding(start = 16.dp)
                .background(
                    color = TripRecordPalette.current.surface,
                    shape = RoundedCornerShape(18.dp),
                )
                .padding(horizontal = 18.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = photoCount.toString(),
                color = TripRecordPalette.current.accent,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "장",
                color = TripRecordPalette.current.secondaryText,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun EmptyPhotoAlbum() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 80.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "아직 사진이 없어요.",
            color = TripRecordPalette.current.secondaryText,
            fontSize = 14.sp,
        )
    }
}

@Composable
private fun PhotoDateGroup(
    group: TripRecordPhotoGroup,
    onPhotoClick: (TripRecordPhotoUiState) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = group.displayDate,
                color = TripRecordPalette.current.headingText,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "${group.photos.size}장",
                color = TripRecordPalette.current.accent,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .background(
                        color = TripRecordPalette.current.primarySoft,
                        shape = RoundedCornerShape(10.dp),
                    )
                    .padding(horizontal = 10.dp, vertical = 7.dp),
            )
        }
        Column(
            modifier = Modifier.padding(top = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            group.photos.chunked(PhotoColumns).forEach { rowPhotos ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    rowPhotos.forEach { photo ->
                        TripPhotoImage(
                            imageBytes = photo.previewBytes?.bytesForDecoding()
                                ?: photo.originalBytes?.bytesForDecoding(),
                            fallbackBytes = photo.originalBytes?.bytesForDecoding(),
                            contentDescription = "${group.displayDate} 여행 사진 확대",
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .semantics {
                                    contentDescription = "${group.displayDate} 여행 사진 확대"
                                }
                                .clickable(role = Role.Button) { onPhotoClick(photo) },
                            placeholderVariant = photo.id.hashCode(),
                            shape = RoundedCornerShape(14.dp),
                        )
                    }
                    repeat(PhotoColumns - rowPhotos.size) {
                        Spacer(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AlbumScrollBar(
    scrollValue: Int,
    maxScrollValue: Int,
    modifier: Modifier = Modifier,
) {
    if (maxScrollValue <= 0) return
    val trackColor = TripRecordPalette.current.line.copy(alpha = 0.65f)
    val thumbColor = TripRecordPalette.current.accent.copy(alpha = 0.9f)

    Canvas(
        modifier = modifier
            .width(4.dp)
            .fillMaxHeight(),
    ) {
        val viewportHeight = size.height
        val contentHeight = viewportHeight + maxScrollValue
        val thumbHeight = (viewportHeight * viewportHeight / contentHeight)
            .coerceAtLeast(44.dp.toPx())
            .coerceAtMost(viewportHeight)
        val progress = scrollValue.toFloat() / maxScrollValue.toFloat()
        val thumbOffset = (viewportHeight - thumbHeight) * progress

        drawRoundRect(
            color = trackColor,
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.width / 2f),
        )
        drawRoundRect(
            color = thumbColor,
            topLeft = androidx.compose.ui.geometry.Offset(0f, thumbOffset),
            size = androidx.compose.ui.geometry.Size(size.width, thumbHeight),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.width / 2f),
        )
    }
}

@Composable
private fun ExpandedTripPhotoViewer(
    locationName: String,
    photos: List<TripRecordPhotoUiState>,
    initialPage: Int,
    onBackClick: () -> Unit,
) {
    val pagerState = rememberPagerState(initialPage = initialPage) { photos.size }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TripRecordPalette.current.background),
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            val photo = photos[page]
            Box(Modifier.fillMaxSize()) {
                TripPhotoImage(
                    imageBytes = photo.previewBytes?.bytesForDecoding()
                        ?: photo.originalBytes?.bytesForDecoding(),
                    fallbackBytes = photo.originalBytes?.bytesForDecoding(),
                    contentDescription = "$locationName 사진 배경",
                    modifier = Modifier
                        .fillMaxSize()
                        .alpha(0.48f),
                    placeholderVariant = photo.id.hashCode(),
                    shape = RectangleShape,
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(TripRecordPalette.current.mediaScrim.copy(alpha = 0.28f)),
                )
                TripPhotoImage(
                    imageBytes = photo.previewBytes?.bytesForDecoding()
                        ?: photo.originalBytes?.bytesForDecoding(),
                    fallbackBytes = photo.originalBytes?.bytesForDecoding(),
                    contentDescription = "$locationName 확대 사진 ${page + 1}",
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(vertical = 104.dp),
                    placeholderVariant = photo.id.hashCode(),
                    shape = RectangleShape,
                    contentScale = ContentScale.Fit,
                )
            }
        }
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 18.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TripIconButton(
                    label = "←",
                    contentDescription = "사진첩으로 돌아가기",
                    onClick = onBackClick,
                    containerColor = Color.Black.copy(alpha = 0.48f),
                    contentColor = TripRecordPalette.current.contentOnMedia,
                )
                Text(
                    text = locationName,
                    color = TripRecordPalette.current.contentOnMedia,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Text(
                text = "${pagerState.currentPage + 1} / ${photos.size}",
                color = TripRecordPalette.current.contentOnMedia,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.48f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 13.dp, vertical = 9.dp),
            )
        }
    }
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
            containerColor = Color.Transparent,
            contentColor = TripRecordPalette.current.text,
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

internal data class TripRecordPhotoGroup(
    val sortDate: String?,
    val displayDate: String,
    val photos: List<TripRecordPhotoUiState>,
)

internal fun groupTripRecordPhotosByDate(
    photos: List<TripRecordPhotoUiState>,
    fallbackDate: String?,
): List<TripRecordPhotoGroup> {
    val normalizedFallback = fallbackDate.toAlbumDate()
    return photos
        .groupBy { photo -> photo.capturedAt.toAlbumDate() ?: normalizedFallback }
        .map { (date, groupedPhotos) ->
            TripRecordPhotoGroup(
                sortDate = date,
                displayDate = date?.toAlbumDisplayDate() ?: UnknownPhotoDate,
                photos = groupedPhotos.sortedBy(TripRecordPhotoUiState::sortOrder),
            )
        }
        .sortedWith(
            compareByDescending<TripRecordPhotoGroup> { it.sortDate != null }
                .thenByDescending(TripRecordPhotoGroup::sortDate),
        )
}

private fun String?.toAlbumDate(): String? {
    val value = this?.trim().orEmpty()
    val match = AlbumDatePattern.find(value) ?: return null
    val year = match.groupValues[1]
    val month = match.groupValues[2].padStart(2, '0')
    val day = match.groupValues[3].padStart(2, '0')
    return "$year-$month-$day"
}

private fun String.toAlbumDisplayDate(): String {
    val (year, month, day) = split('-')
    return "$year. $month. $day"
}

@Preview(
    name = "여행 기록 사진첩",
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

private const val PhotoColumns = 2
private const val UnknownPhotoDate = "날짜 미상"
private val AlbumDatePattern = Regex("(\\d{4})[.\\-/](\\d{1,2})[.\\-/](\\d{1,2})")
