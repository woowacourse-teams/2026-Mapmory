package com.mapmory.shared.presentation.group

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mapmory.shared.analytics.LocalMapmoryAnalytics
import com.mapmory.shared.analytics.emit
import com.mapmory.shared.domain.group.AlbumSummary
import com.mapmory.shared.domain.model.TripRecordSummary
import com.mapmory.shared.domain.model.TripRecordQuery
import com.mapmory.shared.domain.repository.AlbumRepository
import com.mapmory.shared.domain.repository.TripRecordRepository
import com.mapmory.shared.data.remote.MapmoryApiException
import kotlinx.coroutines.launch

@Composable
internal fun LiveAlbumContent(groupId: Long, albums: AlbumRepository, records: TripRecordRepository, shares: AlbumShareCoordinator, navigation: AlbumNavigationState, groupName: String, memberCount: Int, onBack: () -> Unit, onInvite: () -> Unit, onManageGroup: () -> Unit, onPhotoVisibilityChanged: (Boolean) -> Unit, onLeaveGroup: (() -> Unit)? = null, membershipBusy: Boolean = false, recordRevision: Long = 0, onGroupUnavailable: () -> Unit = {}, onCreateRecord: () -> Unit = {}) {
    val analytics = LocalMapmoryAnalytics.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember(groupId) { SnackbarHostState() }
    var list by remember(groupId) { mutableStateOf<List<AlbumSummary>?>(null) }
    var page by remember(groupId) { mutableIntStateOf(0) }
    var more by remember(groupId) { mutableStateOf(false) }
    var detail by navigation.detail
    var selectedPhoto by navigation.selectedPhoto
    var loading by remember(groupId) { mutableStateOf(false) }
    var refreshing by remember(groupId) { mutableStateOf(false) }
    var renameError by remember(groupId) { mutableStateOf<String?>(null) }
    var error by remember(groupId) { mutableStateOf<String?>(null) }
    var pick by navigation.pick
    var add by navigation.add
    var ownRecords by remember(groupId) { mutableStateOf<List<TripRecordSummary>?>(null) }
    var recordPage by remember(groupId) { mutableIntStateOf(0) }
    var recordMore by remember(groupId) { mutableStateOf(false) }
    val operations by shares.operations.collectAsState()
    val storageError by shares.storageError.collectAsState()
    var acknowledge by remember(groupId) { mutableStateOf<AlbumShareOperation?>(null) }
    val busy = loading || membershipBusy || operations.any { it.status == AlbumShareStatus.RUNNING }
    var loadedRecordRevision by remember(groupId) { mutableStateOf<Long?>(null) }
    var rename by navigation.rename
    var menu by remember { mutableStateOf(false) }
    LaunchedEffect(selectedPhoto != null) { onPhotoVisibilityChanged(selectedPhoto != null) }
    DisposableEffect(Unit) { onDispose { onPhotoVisibilityChanged(false) } }
    var name by remember(groupId) { mutableStateOf("") }

    suspend fun load(next: Int = 0) {
        if (loading || shares.isRunning) return
        loading = true; error = null
        try { albums.list(groupId, next).onSuccess {
            list = if (next == 0) it.items else list.orEmpty() + it.items
            page = it.page; more = it.hasNext
        }.onFailure {
            if (it.isMissingGroup()) onGroupUnavailable()
            else error = "앨범을 불러오지 못했어요. 다시 시도해 주세요."
        } } finally { loading = false }
    }
    suspend fun open(id: Long) {
        if (loading || shares.isRunning) return
        loading = true; error = null
        val revision = navigation.revision
        try { albums.detail(groupId, id).onSuccess {
            if (revision == navigation.revision && navigation.showAlbum(it)) analytics.emit("album_viewed", mapOf("execution_mode" to "live"))
        }.onFailure {
            if (revision != navigation.revision) return@onFailure
            when {
                it.isMissingGroup() -> onGroupUnavailable()
                it is MapmoryApiException && it.statusCode == 404 && it.code == "ALBUM_NOT_FOUND" -> {
                    navigation.showList()
                    list = list?.filterNot { album -> album.id == id }
                    error = "앨범이 삭제되었거나 더 이상 볼 수 없어요."
                }
                else -> error = "앨범을 불러오지 못했어요. 다시 시도해 주세요."
            }
        } } finally { loading = false }
    }
    suspend fun loadRecords(next: Int = 0) {
        if (loading || shares.isRunning) return
        loading = true; error = null
        try { records.getTripRecords(TripRecordQuery(page = next)).onSuccess {
            ownRecords = if (next == 0) it.records else ownRecords.orEmpty() + it.records
            recordPage = it.page; recordMore = it.page + 1 < it.totalPages
        }.onFailure { error = "기록을 불러오지 못했어요. 다시 시도해 주세요." } } finally { loading = false }
    }
    val successful = operations.lastOrNull { it.groupId == groupId && it.status == AlbumShareStatus.SUCCESS }
    LaunchedEffect(successful) {
        if (successful != null) {
            pick = false
            load()
            successful.resultAlbumId?.let { open(it) }
            shares.acknowledge(successful)
            scope.launch {
                snackbarHostState.currentSnackbarData?.dismiss()
                snackbarHostState.showSnackbar("앨범에 담았어요", duration = SnackbarDuration.Short)
            }
        }
    }
    LaunchedEffect(groupId, recordRevision, operations.any { it.status == AlbumShareStatus.RUNNING }, membershipBusy) {
        if (!shares.isRunning && !membershipBusy && (list == null || loadedRecordRevision != recordRevision)) {
            load()
            detail?.id?.let { open(it) }
            loadedRecordRevision = recordRevision
        }
    }

    suspend fun refreshContent() {
        if (busy) return
        refreshing = true
        try {
            when {
                pick -> loadRecords()
                detail != null -> open(checkNotNull(detail).id)
                else -> load()
            }
        } finally { refreshing = false }
    }
    LaunchedEffect(error, list, ownRecords, pick) {
        val message = error ?: return@LaunchedEffect
        val hasContent = if (pick) ownRecords != null else list != null
        if (hasContent) {
            val result = snackbarHostState.showSnackbar(message, actionLabel = "다시 시도", duration = SnackbarDuration.Short)
            error = null
            if (result == SnackbarResult.ActionPerformed) refreshContent()
        }
    }
    GroupRefreshContainer(
        refreshing = refreshing, enabled = !busy && selectedPhoto == null && !add && !rename,
        onRefresh = { scope.launch { refreshContent() } }, modifier = Modifier.fillMaxSize(),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
            storageError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            operations.filter { it.groupId == groupId && selectedPhoto == null }.forEach { operation ->
                when (operation.status) {
                    AlbumShareStatus.RUNNING -> Text("기록을 앨범에 담고 있어요. 화면을 이동해도 계속 진행돼요.")
                    AlbumShareStatus.SUCCESS -> Unit
                    AlbumShareStatus.FAILURE, AlbumShareStatus.UNKNOWN -> {
                        Text(if (operation.status == AlbumShareStatus.UNKNOWN) "공유 결과를 확인하지 못했어요. 앨범 목록을 확인해 주세요." else "앨범에 담지 못했어요.")
                        TextButton(onClick = { navigation.showList(); scope.launch { load() } }, enabled = !busy) { Text("앨범 목록 확인") }
                        if (operation.targetAlbumId != null || operation.status == AlbumShareStatus.FAILURE) {
                            TextButton(onClick = { shares.start(operation.groupId, operation.recordId, operation.targetAlbumId) }, enabled = !busy) { Text("공유 다시 시도") }
                        }
                        TextButton(onClick = { acknowledge = operation }, enabled = !busy) { Text("확인 후 작업 종료") }
                    }
                }
            }
            val actions: @Composable () -> Unit = {
                Box {
                    com.mapmory.shared.presentation.triprecord.screen.TripIconButton(
                        label = "•••", contentDescription = "그룹 및 앨범 메뉴", onClick = { menu = true },
                    )
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("이름 변경") }, enabled = !busy, onClick = {
                            menu = false; renameError = null
                            detail?.let { name = it.name; rename = true } ?: onManageGroup()
                        })
                        if (detail == null && onLeaveGroup != null) DropdownMenuItem(
                            text = { Text("그룹 나가기", color = MaterialTheme.colorScheme.error) },
                            enabled = !busy,
                            onClick = { menu = false; onLeaveGroup() },
                        )
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
                    pick -> AlbumRecordPicker(
                        records = ownRecords.orEmpty(), loading = loading, error = error.takeIf { ownRecords == null },
                        hasMore = recordMore, enabled = !busy && storageError == null,
                        status = { record ->
                            when {
                                detail?.myTravelRecordIds?.contains(record.id) == true -> "이미 담음"
                                operations.any { it.groupId == groupId && it.recordId == record.id && it.targetAlbumId == detail?.id && it.status in listOf(AlbumShareStatus.RUNNING, AlbumShareStatus.UNKNOWN, AlbumShareStatus.SUCCESS) } -> "공유 중이거나 확인이 필요한 기록"
                                else -> null
                            }
                        },
                        onBack = { pick = false },
                        onSelect = { shares.start(groupId, it.id, detail?.id) },
                        onMore = { scope.launch { loadRecords(recordPage + 1) } },
                        onRetry = { scope.launch { loadRecords() } },
                    )
                    album != null && selectedPhoto != null && album.photos.isNotEmpty() -> {
                        GroupPhotoScreen(album, album.photos.indexOfFirst { it.imageUri == selectedPhoto }.coerceAtLeast(0),
                            onBack = { selectedPhoto = null },
                            onPageChanged = { _, index -> selectedPhoto = album.photos[index].imageUri })
                    }
                    album != null -> GroupAlbumScreen(album,
                        headerActions = actions,
                        onBack = { navigation.showList(); scope.launch { load() } },
                        onAddPhotos = { if (!busy) { snackbarHostState.currentSnackbarData?.dismiss(); add = true } },
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
                        onAdd = { if (!busy) { snackbarHostState.currentSnackbarData?.dismiss(); add = true } },
                        onAlbum = { scope.launch { open(it.toLong()) } },
                    )
                }
            }
            if (!pick && detail == null && more) TextButton(onClick = { scope.launch { load(page + 1) } }, enabled = !busy) { Text("더 보기") }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (!pick && list == null) error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(androidx.compose.ui.Alignment.BottomCenter).padding(16.dp),
        )
    }
    if (add) AlertDialog(
        onDismissRequest = { add = false },
        title = { Text("앨범에 사진 추가") },
        text = { Column {
            Text("새 기록을 만들거나 내 기록을 앨범에 담아 보세요.")
            TextButton(onClick = { add = false; onCreateRecord() }, enabled = !busy && storageError == null) { Text("새 기록 추가") }
            TextButton(onClick = { add = false; pick = true; scope.launch { loadRecords() } }, enabled = !busy) { Text("내 기록 불러오기") }
        } },
        confirmButton = { TextButton(onClick = { add = false }) { Text("닫기") } },
    )
    acknowledge?.let { operation ->
        AlertDialog(onDismissRequest = { acknowledge = null }, title = { Text("공유 작업을 종료할까요?") },
            text = { Text("서버에서 이미 앨범이 만들어졌을 수 있어요. 목록을 확인한 뒤 종료해 주세요. 종료해도 서버의 앨범은 삭제되지 않아요.") },
            confirmButton = { TextButton(onClick = { shares.acknowledge(operation); acknowledge = null }) { Text("목록 확인 완료 · 작업 종료") } },
            dismissButton = { TextButton(onClick = { acknowledge = null }) { Text("돌아가기") } })
    }
    if (rename) AlertDialog(onDismissRequest = { if (!busy) rename = false }, title = { Text("앨범 이름 변경") },
        text = { OutlinedTextField(name, { name = it; renameError = null }, enabled = !busy,
            isError = renameError != null, supportingText = { renameError?.let { Text(it) } }) },
        confirmButton = { TextButton(enabled = !busy && name.isNotBlank(), onClick = { scope.launch {
            val id = detail?.id ?: return@launch
            loading = true; renameError = null
            try { albums.update(groupId, id, name = name.trim()).onSuccess { detail = it; rename = false }.onFailure { renameError = "이름을 변경하지 못했어요. 다시 시도해 주세요." } } finally { loading = false }
        } }) { Text("저장") } }, dismissButton = { TextButton(onClick = { rename = false }, enabled = !busy) { Text("취소") } })
}
