package com.mapmory.shared.presentation.group

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.mapmory.shared.analytics.LocalMapmoryAnalytics
import com.mapmory.shared.analytics.emit
import com.mapmory.shared.domain.group.AlbumSummary
import com.mapmory.shared.domain.model.TripRecordSummary
import com.mapmory.shared.domain.model.TripRecordQuery
import com.mapmory.shared.domain.repository.AlbumRepository
import com.mapmory.shared.domain.repository.TripRecordRepository
import com.mapmory.shared.presentation.components.MapmoryAsyncImage
import kotlinx.coroutines.launch

@Composable
internal fun LiveAlbumContent(groupId: Long, albums: AlbumRepository, navigation: AlbumNavigationState, groupName: String, memberCount: Int, onBack: () -> Unit, onInvite: () -> Unit, onManageGroup: () -> Unit, onPhotoVisibilityChanged: (Boolean) -> Unit) {
    val analytics = LocalMapmoryAnalytics.current
    val scope = rememberCoroutineScope()
    var list by remember(groupId) { mutableStateOf<List<AlbumSummary>?>(null) }
    var page by remember(groupId) { mutableIntStateOf(0) }
    var more by remember(groupId) { mutableStateOf(false) }
    var detail by navigation.detail
    var selectedPhoto by navigation.selectedPhoto
    var loading by remember(groupId) { mutableStateOf(false) }
    var error by remember(groupId) { mutableStateOf<String?>(null) }
    val busy = loading
    var menu by remember { mutableStateOf(false) }
    LaunchedEffect(selectedPhoto != null) { onPhotoVisibilityChanged(selectedPhoto != null) }
    DisposableEffect(Unit) { onDispose { onPhotoVisibilityChanged(false) } }

    suspend fun load(next: Int = 0) {
        if (loading) return
        loading = true; error = null
        try { albums.list(groupId, next).onSuccess {
            list = if (next == 0) it.items else list.orEmpty() + it.items
            page = it.page; more = it.hasNext
        }.onFailure { error = "앨범을 불러오지 못했어요. 다시 시도해 주세요." } } finally { loading = false }
    }
    suspend fun open(id: Long) {
        if (loading) return
        loading = true; error = null
        val revision = navigation.revision
        try { albums.detail(groupId, id).onSuccess {
            if (revision == navigation.revision && navigation.showAlbum(it)) analytics.emit("album_viewed", mapOf("execution_mode" to "live"))
        }.onFailure { error = "앨범이 없거나 접근할 수 없어요. 다시 확인해 주세요." } } finally { loading = false }
    }
    LaunchedEffect(groupId) { load() }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
        val actions: @Composable () -> Unit = {
            Box {
                TextButton(onClick = { menu = true }) { Text("⋮") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("새로고침") }, enabled = !busy, onClick = {
                        menu = false; scope.launch { detail?.let { open(it.id) } ?: load() }
                    })
                    if (detail == null) DropdownMenuItem(text = { Text("그룹 이름 변경") }, enabled = !busy, onClick = {
                        menu = false; onManageGroup()
                    })
                }
            }
        }
        Box(Modifier.weight(1f)) {
            val album = detail?.let { value ->
                GroupAlbum(value.id.toString(), value.name, value.region.name,
                    listOfNotNull(value.startDate, value.endDate).distinct().joinToString(" – "),
                    value.entries.flatMap { entry -> entry.photos.map { photo ->
                        GroupPhoto(photo.id.toString(), entry.travelRecordId, entry.owner.memberId.toString(), entry.owner.nickname, photo.viewUrl)
                    } }, contributorNames = value.entries.distinctBy { it.owner.memberId }.map { it.owner.nickname })
            }
            when {
                album != null && selectedPhoto != null && album.photos.isNotEmpty() -> {
                    GroupPhotoScreen(album, album.photos.indexOfFirst { it.imageUri == selectedPhoto }.coerceAtLeast(0),
                        onBack = { selectedPhoto = null },
                        onPageChanged = { _, index -> selectedPhoto = album.photos[index].imageUri })
                }
                album != null -> GroupAlbumScreen(album,
                    headerActions = actions,
                    onBack = { navigation.showList(); scope.launch { load() } },
                    onAddPhotos = {}, sharingEnabled = false,
                    onPhoto = { index -> selectedPhoto = album.photos[index].imageUri; analytics.emit("album_photo_viewed", mapOf("execution_mode" to "live")) })
                else -> GroupHomeScreen(
                    group = GroupSummary(groupId.toString(), groupName, memberCount, list.orEmpty().map { item ->
                        GroupAlbum(item.id.toString(), item.name, item.region.name,
                            listOfNotNull(item.startDate, item.endDate).distinct().joinToString(" – "),
                            item.coverPhoto?.let { listOf(GroupPhoto("cover-${item.id}", 0, "", "", it.viewUrl)) }.orEmpty(),
                            photoCount = item.photoCount, contributorNames = item.contributors.map { it.nickname })
                    }),
                    headerActions = actions,
                    onBack = onBack, onInvite = onInvite,
                    onAdd = {}, sharingEnabled = false,
                    onAlbum = { scope.launch { open(it.toLong()) } },
                )
            }
        }
        if (detail == null && more) TextButton(onClick = { scope.launch { load(page + 1) } }, enabled = !busy) { Text("더 보기") }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}
