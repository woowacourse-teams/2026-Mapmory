package com.mapmory.shared.presentation.triprecord.screen

import com.mapmory.shared.presentation.components.MapmoryPhotoExpansion
import com.mapmory.shared.presentation.components.PhotoListScrollBar
import com.mapmory.shared.presentation.components.ScrollToTopEffect
import com.mapmory.shared.presentation.components.MapmoryPhotoViewer
import com.mapmory.shared.presentation.components.MapmoryAsyncImage
import com.mapmory.shared.presentation.components.LocalMapmoryImageTransitionScope
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Dp
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
    onForwardHandlerChanged: ((() -> Boolean)?) -> Unit = {},
    modifier: Modifier = Modifier,
    initialLocationName: String? = null,
    initialLatestDate: String? = null,
    initialPhotoCount: Int? = null,
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    var expandedPhotoIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    var forwardPhotoIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    val albumScrollState = rememberLazyListState()
    ScrollToTopEffect(albumScrollState, enabled = expandedPhotoIndex == null && uiState is TripRecordDetailUiState.Success)
    var photoColumns by rememberSaveable { mutableStateOf(2) }
    val latestPhotoViewerBackHandler by rememberUpdatedState {
        forwardPhotoIndex = expandedPhotoIndex
        expandedPhotoIndex = null
        true
    }

    DisposableEffect(expandedPhotoIndex) {
        onInternalBackHandlerChanged(
            if (expandedPhotoIndex == null) null else latestPhotoViewerBackHandler,
        )
        onDispose { onInternalBackHandlerChanged(null) }
    }

    val latestForwardHandler by rememberUpdatedState {
        val index = forwardPhotoIndex
        val photoCount = (uiState as? TripRecordDetailUiState.Success)?.record?.photos?.size ?: 0
        if (expandedPhotoIndex == null && index != null && index in 0 until photoCount) {
            expandedPhotoIndex = index
            forwardPhotoIndex = null
            true
        } else false
    }
    DisposableEffect(Unit) {
        onForwardHandlerChanged { latestForwardHandler() }
        onDispose { onForwardHandlerChanged(null) }
    }

    TripRecordBackground(modifier = modifier) {
        when (uiState) {
            TripRecordDetailUiState.Idle,
            TripRecordDetailUiState.Loading,
            -> TripRecordDetailSkeleton(
                modifier = Modifier.fillMaxSize(),
                locationName = initialLocationName,
                latestDate = initialLatestDate,
                photoCount = initialPhotoCount,
                columnCount = photoColumns,
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
                            columnCount = photoColumns,
                            onColumnCountChange = { photoColumns = it },
                            onBackClick = onBackClick,
                            onEditClick = onEditClick,
                            onDeleteClick = { showDeleteDialog = true },
                            onPhotoClick = { photo ->
                                forwardPhotoIndex = null
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
    scrollState: LazyListState,
    columnCount: Int,
    onColumnCountChange: (Int) -> Unit,
    onPhotoClick: (TripRecordPhotoUiState) -> Unit,
) {
    var isPinching by remember { mutableStateOf(false) }
    val photoRows = remember(groups, columnCount) {
        groups.map { group -> group.photos.chunked(columnCount) }
    }
    Column(Modifier.fillMaxSize()) {
        TripRecordTopBar(
            title = "",
            onBackClick = onBackClick,
            trailing = {
                DetailMoreButton(
                    onEditClick = onEditClick,
                    onDeleteClick = onDeleteClick,
                )
            },
        )
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            val photoSize = animateDpAsState(
                targetValue = ((maxWidth - 56.dp - 8.dp * (columnCount - 1)) / columnCount)
                    .coerceAtLeast(1.dp),
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                label = "albumPhotoSize",
            )
            LazyColumn(
                state = scrollState,
                userScrollEnabled = !isPinching,
                contentPadding = PaddingValues(start = 24.dp, top = 32.dp, end = 32.dp, bottom = 36.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .photoGridPinch(columnCount, onColumnCountChange, { isPinching = it })
                    .navigationBarsPadding(),
            ) {
                item(key = "heading", contentType = "heading") { AlbumHeading(record.locationName) }
                if (groups.isEmpty()) {
                    item(key = "empty") { EmptyPhotoAlbum() }
                } else {
                    groups.forEachIndexed { index, group ->
                        item(key = "date:${group.displayDate}", contentType = "date") {
                            Text(
                                text = group.displayDate,
                                color = TripRecordPalette.current.headingText,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.animateItem().padding(top = if (index == 0) 32.dp else 34.dp, bottom = 14.dp),
                            )
                        }
                        itemsIndexed(
                            photoRows[index],
                            key = { _, photos -> "row:${photos.first().id}" },
                            contentType = { _, _ -> "photos" },
                        ) { rowIndex, photos ->
                            AlbumPhotoRow(
                                photos, group.displayDate, columnCount, { photoSize.value }, onPhotoClick,
                                Modifier.animateItem().padding(top = if (rowIndex == 0) 0.dp else 8.dp),
                            )
                        }
                    }
                }
            }
            PhotoListScrollBar(
                listState = scrollState,
                contentKey = groups to columnCount,
                testTag = "album-scrollbar",
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
private fun AlbumPhotoRow(
    photos: List<TripRecordPhotoUiState>,
    displayDate: String,
    columnCount: Int,
    photoSize: () -> Dp,
    onPhotoClick: (TripRecordPhotoUiState) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().clipToBounds(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        photos.forEach { photo ->
            MapmoryAsyncImage(
                imageBytes = photo.previewBytes?.bytesForDecoding()
                    ?: photo.originalBytes?.bytesForDecoding(),
                imageUri = photo.previewUri ?: photo.localOriginalUri ?: photo.fullResolutionUri,
                cacheKey = "trip-preview:${photo.id}",
                fallbackUri = photo.fullResolutionUri,
                fallbackBytes = photo.originalBytes?.bytesForDecoding(),
                contentDescription = "$displayDate 여행 사진 확대",
                sharedImageKey = photo.id,
                sharedCornerRadius = 14.dp,
                onClick = { onPhotoClick(photo) },
                modifier = Modifier
                    .layout { measurable, _ ->
                        val side = photoSize().roundToPx().coerceAtLeast(1)
                        val placeable = measurable.measure(Constraints.fixed(side, side))
                        layout(side, side) { placeable.placeRelative(0, 0) }
                    }
                    .semantics {
                        contentDescription = "$displayDate 여행 사진 확대"
                    },
                shape = RoundedCornerShape(14.dp),
            )
        }
        repeat(columnCount - photos.size) {
            Spacer(
                modifier = Modifier
                    .layout { measurable, _ ->
                        val side = photoSize().roundToPx().coerceAtLeast(1)
                        val placeable = measurable.measure(Constraints.fixed(side, side))
                        layout(side, side) { placeable.placeRelative(0, 0) }
                    },
            )
        }
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
            var zoom by remember(photo.id) { mutableStateOf(1f) }
            var pan by remember(photo.id) { mutableStateOf(Offset.Zero) }
            var viewportSize by remember(photo.id) { mutableStateOf(IntSize.Zero) }
            val transformableState = rememberTransformableState { _, zoomChange, panChange, _ ->
                val nextZoom = (zoom * zoomChange).coerceIn(1f, 4f)
                val maxPanX = viewportSize.width * (nextZoom - 1f) / 2f
                val maxPanY = viewportSize.height * (nextZoom - 1f) / 2f
                zoom = nextZoom
                pan = if (nextZoom == 1f) Offset.Zero else Offset(
                    x = (pan.x + panChange.x).coerceIn(-maxPanX, maxPanX),
                    y = (pan.y + panChange.y).coerceIn(-maxPanY, maxPanY),
                )
            }
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
                        .padding(vertical = 104.dp)
                        .clipToBounds()
                        .onSizeChanged { viewportSize = it }
                        .transformable(
                            state = transformableState,
                            canPan = { zoom > 1f },
                            lockRotationOnZoomPan = true,
                        )
                        .pointerInput(photo.id) {
                            detectTapGestures(
                                onDoubleTap = {
                                    if (zoom > 1f) {
                                        zoom = 1f
                                        pan = Offset.Zero
                                    } else {
                                        zoom = 2f
                                    }
                                },
                            )
                        }
                        .graphicsLayer {
                            scaleX = zoom
                            scaleY = zoom
                            translationX = pan.x
                            translationY = pan.y
                        },
                    shape = RectangleShape,
                    sharedCornerRadius = 14.dp,
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

private const val UnknownPhotoDate = "날짜 미상"
private val AlbumDatePattern = Regex("(\\d{4})[.\\-/](\\d{1,2})[.\\-/](\\d{1,2})")
private val KoreanWeekdays = listOf("월", "화", "수", "목", "금", "토", "일")
