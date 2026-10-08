package com.mapmory.shared.presentation.triprecord.screen

import com.mapmory.shared.presentation.components.MapmoryPhotoExpansion
import com.mapmory.shared.presentation.components.MapmoryPhotoViewer
import com.mapmory.shared.presentation.components.MapmoryAsyncImage
import com.mapmory.shared.presentation.components.LocalMapmoryImageTransitionScope
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mapmory.shared.presentation.triprecord.state.TripRecordDetailUiState
import com.mapmory.shared.presentation.triprecord.state.TripRecordItemUiState
import com.mapmory.shared.presentation.triprecord.state.TripRecordPhotoUiState
import com.mapmory.shared.presentation.triprecord.state.localOriginalUri
import com.mapmory.shared.presentation.photo.SelectedPhoto
import com.mapmory.shared.presentation.photo.rememberPhotoLibraryActions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import com.mapmory.shared.preview.PreviewSurface
import com.mapmory.shared.preview.previewUiRecords
import kotlinx.datetime.LocalDate

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
    initialLocationName: String? = null,
    initialLatestDate: String? = null,
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    var expandedPhotoIndex by remember { mutableStateOf<Int?>(null) }
    val albumScrollState = rememberScrollState()
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
            -> TripRecordDetailSkeleton(
                modifier = Modifier.fillMaxSize(),
                locationName = initialLocationName,
                latestDate = initialLatestDate,
                onBackClick = onBackClick,
            )

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
                    groupTripRecordPhotosByDate(record.photos)
                }
                val orderedPhotos = remember(groups) { groups.flatMap(TripRecordPhotoGroup::photos) }
                val selectedIndex = expandedPhotoIndex

                MapmoryPhotoExpansion(selectedIndex) { targetIndex ->
                    if (targetIndex != null && orderedPhotos.isNotEmpty()) {
                        ExpandedTripPhotoViewer(
                            locationName = record.locationName,
                            photos = orderedPhotos,
                            initialPage = targetIndex.coerceIn(orderedPhotos.indices),
                            onBackClick = { expandedPhotoIndex = null },
                        )
                    } else {
                        TripRecordPhotoAlbum(
                            record = record,
                            groups = groups,
                            scrollState = albumScrollState,
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
    scrollState: androidx.compose.foundation.ScrollState,
    onPhotoClick: (TripRecordPhotoUiState) -> Unit,
) {
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
internal fun AlbumHeading(
    locationName: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "$locationName 사진첩",
                color = TripRecordPalette.current.headingText,
                fontSize = 28.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "날짜별로 모아둔 여행 사진이에요.",
                color = TripRecordPalette.current.secondaryText,
                fontSize = 14.sp,
                modifier = Modifier.padding(top = 10.dp),
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
            text = "이 기록에 추가된 사진이 없어요.",
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
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = group.displayDate,
                color = TripRecordPalette.current.headingText,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
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
                        MapmoryAsyncImage(
                            imageBytes = photo.previewBytes?.bytesForDecoding()
                                ?: photo.originalBytes?.bytesForDecoding(),
                            imageUri = photo.previewUri ?: photo.localOriginalUri ?: photo.fullResolutionUri,
                            cacheKey = "trip-preview:${photo.id}",
                            fallbackUri = photo.fullResolutionUri,
                            fallbackBytes = photo.originalBytes?.bytesForDecoding(),
                            contentDescription = "${group.displayDate} 여행 사진 확대",
                            sharedImageKey = photo.id,
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .semantics {
                                    contentDescription = "${group.displayDate} 여행 사진 확대"
                                }
                                .clickable(role = Role.Button) { onPhotoClick(photo) },
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
    val imageTransitionActive =
        LocalMapmoryImageTransitionScope.current?.isTransitionActive == true
    val currentPhoto = photos[pagerState.currentPage]
    var localPreview by remember(currentPhoto.id) { mutableStateOf<ByteArray?>(null) }
    var localLookupFinished by remember(currentPhoto.id) { mutableStateOf(false) }
    val photoLibrary = rememberPhotoLibraryActions({}, {}, {}, {}, {}, {}, {})
    LaunchedEffect(currentPhoto.id) {
        val localId = currentPhoto.localPhotoId
        if (localId != null && currentPhoto.localOriginalUri == null) {
            localPreview = suspendCancellableCoroutine { continuation ->
                photoLibrary.loadFullResolutionPreview(
                    SelectedPhoto(localId, currentPhoto.displayName, null),
                ) { bytes ->
                    if (continuation.isActive) continuation.resume(bytes)
                }
            }
        }
        localLookupFinished = true
    }

    MapmoryPhotoViewer(
        title = locationName,
        onClose = onBackClick,
        closeContentDescription = "사진첩으로 돌아가기",
        trailingContent = {
            Text(
                "${pagerState.currentPage + 1} / ${photos.size}",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            )
        },
    ) {
        HorizontalPager(
            state = pagerState,
            key = { photos[it].id },
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            val photo = photos[page]
            val isCurrent = page == pagerState.currentPage
            val localBytes = localPreview.takeIf { isCurrent }
            val awaitingLocalPhoto = isCurrent && !localLookupFinished &&
                photo.localPhotoId != null && photo.localOriginalUri == null
            val usePreview = imageTransitionActive || awaitingLocalPhoto
            Box(Modifier.fillMaxSize()) {
                MapmoryAsyncImage(
                    imageBytes = if (usePreview) {
                        photo.previewBytes?.bytesForDecoding() ?: photo.originalBytes?.bytesForDecoding()
                    } else {
                        localBytes ?: photo.previewBytes?.bytesForDecoding()
                            ?: photo.originalBytes?.bytesForDecoding()
                    },
                    imageUri = when {
                        usePreview -> photo.previewUri ?: photo.localOriginalUri
                        localBytes != null -> null
                        !isCurrent -> photo.previewUri
                        else -> photo.localOriginalUri ?: photo.fullResolutionUri ?: photo.previewUri
                    },
                    cacheKey = if (usePreview) "trip-preview:${photo.id}" else "trip-full:${photo.id}",
                    blackLoadingBackground = true,
                    fallbackUri = when {
                        usePreview -> null
                        photo.localOriginalUri != null -> photo.fullResolutionUri ?: photo.previewUri
                        else -> photo.previewUri
                    },
                    fallbackBytes = photo.originalBytes?.bytesForDecoding(),
                    contentDescription = "$locationName 확대 사진 ${page + 1}",
                    sharedImageKey = photo.id.takeIf { isCurrent },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(vertical = 104.dp),
                    shape = RectangleShape,
                    contentScale = ContentScale.Fit,
                )
            }
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
): List<TripRecordPhotoGroup> {
    return photos
        .groupBy { photo -> photo.capturedAt.toAlbumDate() }
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

internal fun String?.toAlbumDate(): String? {
    val value = this?.trim().orEmpty()
    val match = AlbumDatePattern.find(value) ?: return null
    val year = match.groupValues[1]
    val month = match.groupValues[2].padStart(2, '0')
    val day = match.groupValues[3].padStart(2, '0')
    return "$year-$month-$day".takeIf { runCatching { LocalDate.parse(it) }.isSuccess }
}

internal fun String.toAlbumDisplayDate(): String {
    val (year, month, day) = split('-')
    val weekday = runCatching { LocalDate.parse(this).dayOfWeek.ordinal }
        .getOrNull()
        ?.let(KoreanWeekdays::getOrNull)
    return "$year. $month. $day${weekday?.let { " ($it)" }.orEmpty()}"
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
private val KoreanWeekdays = listOf("월", "화", "수", "목", "금", "토", "일")
