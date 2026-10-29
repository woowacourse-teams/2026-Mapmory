package com.mapmory.shared.presentation.group

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview
import com.mapmory.shared.presentation.components.MapmoryAsyncImage
import com.mapmory.shared.presentation.components.MapmoryPhotoViewer
import com.mapmory.shared.presentation.triprecord.screen.TripRecordBackground
import com.mapmory.shared.presentation.triprecord.screen.TripRecordPalette

@Composable
internal fun GroupListScreen(
    state: GroupListUiState,
    onCreate: () -> Unit,
    onJoin: () -> Unit,
    onGroup: (String) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    onScrolled: () -> Unit = {},
    headerActions: (@Composable () -> Unit)? = null,
) {
    val scrollState = rememberLazyListState()
    GroupScrollObserver(scrollState, { scrollState.isScrollInProgress }, onScrolled)
    Column(modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        Text("내 그룹", fontSize = 28.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 24.dp, bottom = 24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            GroupButton("+ 새 그룹 만들기", onCreate, Modifier.weight(1f))
            GroupButton("코드로 참여", onJoin, Modifier.weight(1f), outlined = true)
        }
        when (state) {
            GroupListUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            is GroupListUiState.Error -> GroupEmpty(
                "그룹을 불러오지 못했어요", state.message, "다시 시도", onRetry)
            is GroupListUiState.Content -> if (state.groups.isEmpty()) {
                GroupEmpty("아직 참여한 그룹이 없어요", "새 그룹을 만들고 지인을 초대해 보세요!\n함께한 여행의 사진을 한곳에 모을 수 있어요.")
            } else {
                LazyColumn(state = scrollState, contentPadding = PaddingValues(vertical = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(state.groups, key = { it.id }) { group ->
                        GroupCard(onClick = { onGroup(group.id) }) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                GroupImage(group.coverUri?.let { GroupPhoto("cover", 0, "", "", it) } ?: group.albums.firstOrNull()?.photos?.firstOrNull(),
                                    Modifier.size(88.dp), "${group.name} 대표 사진")
                                Column(Modifier.weight(1f).padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(group.name, fontWeight = FontWeight.Bold, maxLines = 2)
                                    GroupMuted("멤버 ${group.memberCount}명 · 앨범 ${group.albumCount}개")
                                }
                                Text("›", Modifier.padding(end = 16.dp), color = TripRecordPalette.current.muted)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun GroupHomeScreen(
    group: GroupSummary,
    onBack: () -> Unit,
    onInvite: () -> Unit,
    onAdd: () -> Unit,
    onAlbum: (String) -> Unit,
    modifier: Modifier = Modifier,
    onScrolled: () -> Unit = {},
    headerActions: (@Composable () -> Unit)? = null,
    sharingEnabled: Boolean = true,
) {
    val scrollState = rememberLazyListState()
    GroupScrollObserver(scrollState, { scrollState.isScrollInProgress }, onScrolled)
    Column(modifier.fillMaxSize()) {
        GroupHeader(group.name, onBack, headerActions)
        LazyColumn(state = scrollState, modifier = Modifier.weight(1f), contentPadding = PaddingValues(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)) {
            item { GroupMuted("멤버 ${group.memberCount}명 · 함께 남긴 여행 사진") }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    GroupButton("멤버 초대", onInvite, Modifier.weight(1f), outlined = true)
                    if (sharingEnabled && group.albums.isNotEmpty()) GroupButton("앨범 추가", onAdd, Modifier.weight(1f))
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("함께 남긴 앨범", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    GroupMuted("앨범 ${group.albumCount}개")
                }
            }
            if (group.albums.isEmpty()) item {
                Column(Modifier.fillMaxWidth().padding(vertical = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    EmptySymbol()
                    Text("아직 함께 남긴 사진이 없어요", fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 24.dp, bottom = 12.dp))
                    GroupMuted(if (sharingEnabled) "첫 앨범을 만들거나 내 여행 기록을 공유해 보세요." else "공유된 앨범이 아직 없어요.")
                    Spacer(Modifier.height(24.dp))
                    if (sharingEnabled) GroupButton("첫 앨범 추가하기", onAdd, Modifier.fillMaxWidth())
                }
            }
            items(group.albums, key = { it.id }) { album ->
                GroupCard(onClick = { onAlbum(album.id) }) {
                    Column {
                        GroupImage(album.photos.firstOrNull(), Modifier.fillMaxWidth().height(172.dp), "${album.name} 대표 사진")
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            GroupMuted(album.place)
                            Text(album.name, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            GroupMuted("${album.date} · 사진 ${album.photoCount}장")
                            GroupMuted("함께 올린 사람 · ${album.contributors.joinToString(", ")}")
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun GroupAlbumScreen(
    album: GroupAlbum,
    onBack: () -> Unit,
    onAddPhotos: () -> Unit,
    onPhoto: (Int) -> Unit,
    modifier: Modifier = Modifier,
    onScrolled: () -> Unit = {},
    headerActions: (@Composable () -> Unit)? = null,
    sharingEnabled: Boolean = true,
) {
    val scrollState = rememberLazyGridState()
    GroupScrollObserver(scrollState, { scrollState.isScrollInProgress }, onScrolled)
    Column(modifier.fillMaxSize()) {
        GroupHeader(album.name, onBack, headerActions)
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            GroupMuted("${album.place} · ${album.date}")
            Text("사진 ${album.photoCount}장", fontWeight = FontWeight.Bold, fontSize = 20.sp)
            GroupMuted("함께 올린 사람 · ${album.contributors.joinToString(", ")}")
        }
        if (album.photos.isEmpty()) {
            Box(Modifier.weight(1f)) { GroupEmpty("아직 사진이 없어요", "이 앨범에 첫 사진을 남겨 보세요.") }
        } else {
            LazyVerticalGrid(state = scrollState, columns = GridCells.Fixed(2), modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(album.photos, key = { it.id }) { photo ->
                    GroupCard(onClick = { onPhoto(album.photos.indexOf(photo)) }) {
                        Column {
                            GroupImage(photo, Modifier.fillMaxWidth().aspectRatio(1f), "${photo.uploaderName}님이 올린 사진")
                            Text(photo.uploaderName, Modifier.padding(12.dp), fontSize = 13.sp)
                        }
                    }
                }
            }
        }
        if (sharingEnabled) GroupButton("이 앨범에 사진 추가", onAddPhotos,
            Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp), tall = true)
    }
}

@Composable
internal fun GroupPhotoScreen(
    album: GroupAlbum, initialPage: Int, onBack: () -> Unit,
    currentMemberId: String? = null, onDelete: (GroupPhoto) -> Unit = {},
    onPageChanged: (Int, Int) -> Unit = { _, _ -> },
) {
    val pager = rememberPagerState(initialPage = initialPage.coerceIn(album.photos.indices)) { album.photos.size }
    val latestPageChanged = rememberUpdatedState(onPageChanged)
    LaunchedEffect(pager) {
        var previous = pager.settledPage
        snapshotFlow { pager.settledPage }.distinctUntilChanged().drop(1).collect { page ->
            if (page != previous) latestPageChanged.value(previous, page)
            previous = page
        }
    }
    MapmoryPhotoViewer(title = "${pager.currentPage + 1} / ${album.photos.size}", onClose = onBack,
        closeContentDescription = "사진 닫기", bottomContent = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val photo = album.photos[pager.currentPage]
                Text("${photo.uploaderName}님이 올린 사진", color = Color.White,
                    modifier = Modifier.padding(24.dp))
                if (photo.uploaderId == currentMemberId) {
                    TextButton(onClick = { onDelete(photo) }) { Text("사진 삭제", color = Color.White) }
                }
            }
        }) {
        HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
            MapmoryAsyncImage(imageBytes = null, imageUri = album.photos[page].imageUri,
                contentDescription = "${album.photos[page].uploaderName}님이 올린 사진", shape = RoundedCornerShape(0.dp),
                contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
        }
    }
}

@Composable
internal fun GroupHeader(title: String, onBack: () -> Unit, trailing: (@Composable () -> Unit)? = null) {
    com.mapmory.shared.presentation.triprecord.screen.TripRecordTopBar(
        title = title,
        onBackClick = onBack,
        trailing = trailing,
    )
}

@Composable
internal fun GroupButton(
    text: String, onClick: () -> Unit, modifier: Modifier = Modifier,
    outlined: Boolean = false, enabled: Boolean = true, tall: Boolean = false,
) {
    val shape = RoundedCornerShape(14.dp)
    if (outlined) OutlinedButton(onClick, modifier.heightIn(min = if (tall) 56.dp else 48.dp),
        enabled = enabled, shape = shape, border = BorderStroke(1.dp, TripRecordPalette.current.border),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = TripRecordPalette.current.surface)) {
        Text(text, fontSize = 16.sp, textAlign = TextAlign.Center)
    } else Button(onClick, modifier.heightIn(min = if (tall) 56.dp else 48.dp), enabled = enabled, shape = shape) {
        Text(text, fontSize = 16.sp, textAlign = TextAlign.Center)
    }
}

@Composable
internal fun GroupCard(onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    val colors = TripRecordPalette.current
    if (onClick == null) {
        Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
            color = colors.surface, border = BorderStroke(1.dp, colors.border), content = content)
    } else {
        Surface(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
            color = colors.surface, border = BorderStroke(1.dp, colors.border), content = content)
    }
}

@Composable
private fun GroupImage(photo: GroupPhoto?, modifier: Modifier, description: String) {
    MapmoryAsyncImage(imageBytes = null, imageUri = photo?.imageUri, contentDescription = description,
        modifier = modifier, shape = RoundedCornerShape(0.dp))
}

@Composable
internal fun GroupMuted(text: String) {
    Text(text, color = TripRecordPalette.current.muted, fontSize = 14.sp, lineHeight = 22.sp)
}

@Composable
private fun EmptySymbol() {
    Surface(shape = RoundedCornerShape(20.dp), color = TripRecordPalette.current.surface,
        border = BorderStroke(1.dp, TripRecordPalette.current.border)) {
        val color = TripRecordPalette.current.primary
        Canvas(Modifier.padding(24.dp).size(48.dp)) {
            val stroke = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx())
            drawRect(color = color, style = stroke)
            val mountains = androidx.compose.ui.graphics.Path().apply {
                moveTo(0f, size.height * 0.8f)
                lineTo(size.width * 0.3f, size.height * 0.42f)
                lineTo(size.width * 0.55f, size.height * 0.68f)
                lineTo(size.width * 0.76f, size.height * 0.5f)
                lineTo(size.width, size.height * 0.82f)
            }
            drawPath(mountains, color, style = stroke)
        }
    }
}

@Composable
private fun GroupEmpty(title: String, message: String, action: String? = null, onAction: () -> Unit = {}) {
    Column(Modifier.fillMaxSize().padding(vertical = 48.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center) {
        EmptySymbol()
        Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 24.dp, bottom = 12.dp))
        Text(message, color = TripRecordPalette.current.muted, fontSize = 14.sp,
            lineHeight = 22.sp, textAlign = TextAlign.Center)
        if (action != null) TextButton(onClick = onAction) { Text(action) }
    }
}

@Preview
@Composable
private fun GroupListPreview() = TripRecordBackground {
    GroupListScreen(GroupListUiState.Content(listOf(previewGroup)), {}, {}, {}, {})
}

@Preview
@Composable
private fun GroupEmptyPreview() = TripRecordBackground {
    GroupListScreen(GroupListUiState.Content(), {}, {}, {}, {})
}

@Preview
@Composable
private fun GroupHomePreview() = TripRecordBackground { GroupHomeScreen(previewGroup, {}, {}, {}, {}) }

@Preview
@Composable
private fun GroupEmptyHomePreview() = TripRecordBackground {
    GroupHomeScreen(previewGroup.copy(albums = emptyList()), {}, {}, {}, {})
}

@Preview
@Composable
private fun GroupAlbumPreview() = TripRecordBackground { GroupAlbumScreen(previewGroup.albums.first(), {}, {}, {}) }

/** One semantic scroll event after settling, never per frame or during state restoration. */
@Composable
internal fun GroupScrollObserver(key: Any, isScrolling: () -> Boolean, onScrolled: () -> Unit) {
    val latest = rememberUpdatedState(onScrolled)
    LaunchedEffect(key) {
        var wasScrolling = false
        snapshotFlow(isScrolling).collect { scrolling ->
            if (wasScrolling && !scrolling) latest.value()
            wasScrolling = scrolling
        }
    }
}
