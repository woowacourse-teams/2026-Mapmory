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
import com.mapmory.shared.data.remote.MapmoryApiException
import com.mapmory.shared.presentation.components.MapmoryAsyncImage
import kotlinx.coroutines.launch

@Composable
internal fun LiveAlbumContent(groupId: Long, albums: AlbumRepository, records: TripRecordRepository, shares: AlbumShareCoordinator, navigation: AlbumNavigationState, groupName: String, memberCount: Int, onBack: () -> Unit, onInvite: () -> Unit, onManageGroup: () -> Unit, onPhotoVisibilityChanged: (Boolean) -> Unit, onLeaveGroup: (() -> Unit)? = null, membershipBusy: Boolean = false, recordRevision: Long = 0, onGroupUnavailable: () -> Unit = {}) {
    val analytics = LocalMapmoryAnalytics.current
    val scope = rememberCoroutineScope()
    var list by remember(groupId) { mutableStateOf<List<AlbumSummary>?>(null) }
    var page by remember(groupId) { mutableIntStateOf(0) }
    var more by remember(groupId) { mutableStateOf(false) }
    var detail by navigation.detail
    var selectedPhoto by navigation.selectedPhoto
    var loading by remember(groupId) { mutableStateOf(false) }
    var error by remember(groupId) { mutableStateOf<String?>(null) }
    var pick by navigation.pick
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
            if (detail?.id == successful.resultAlbumId) open(checkNotNull(successful.resultAlbumId))
        }
    }
    LaunchedEffect(groupId, recordRevision, operations.any { it.status == AlbumShareStatus.RUNNING }, membershipBusy) {
        if (!shares.isRunning && !membershipBusy && (list == null || loadedRecordRevision != recordRevision)) {
            load()
            detail?.id?.let { open(it) }
            loadedRecordRevision = recordRevision
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
        storageError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        operations.filter { it.groupId == groupId && selectedPhoto == null }.forEach { operation ->
            when (operation.status) {
                AlbumShareStatus.RUNNING -> Text("기록을 앨범에 담고 있어요. 화면을 이동해도 계속 진행돼요.")
                AlbumShareStatus.SUCCESS -> {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Text("앨범에 담았어요", Modifier.weight(1f))
                        TextButton(onClick = { scope.launch { open(checkNotNull(operation.resultAlbumId)) } }, enabled = !busy) { Text("보기") }
                        TextButton(onClick = { shares.acknowledge(operation) }) { Text("확인") }
                    }
                }
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
                    DropdownMenuItem(text = { Text("새로고침") }, enabled = !busy, onClick = {
                        menu = false; scope.launch { detail?.let { open(it.id) } ?: load() }
                    })
                    DropdownMenuItem(text = { Text("이름 변경") }, enabled = !busy, onClick = {
                        menu = false
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
                album != null && selectedPhoto != null && album.photos.isNotEmpty() -> {
                    GroupPhotoScreen(album, album.photos.indexOfFirst { it.imageUri == selectedPhoto }.coerceAtLeast(0),
                        onBack = { selectedPhoto = null },
                        onPageChanged = { _, index -> selectedPhoto = album.photos[index].imageUri })
                }
                album != null -> GroupAlbumScreen(album,
                    headerActions = actions,
                    onBack = { navigation.showList(); scope.launch { load() } },
                    onAddPhotos = { if (!busy) { pick = true; scope.launch { loadRecords() } } },
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
                    onAdd = { if (!busy) { pick = true; scope.launch { loadRecords() } } },
                    onAlbum = { scope.launch { open(it.toLong()) } },
                )
            }
        }
        if (detail == null && more) TextButton(onClick = { scope.launch { load(page + 1) } }, enabled = !busy) { Text("더 보기") }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
    if (pick) AlertDialog(onDismissRequest = { if (!busy) pick = false }, title = { Text("내 기록 선택") },
        text = { Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
            if (ownRecords?.isEmpty() == true) Text("아직 저장된 기록이 없어요.")
            ownRecords.orEmpty().forEach { record ->
                val already = detail?.myTravelRecordIds?.contains(record.id) == true
                val pending = operations.any { it.groupId == groupId && it.recordId == record.id && it.targetAlbumId == detail?.id && it.status in listOf(AlbumShareStatus.RUNNING, AlbumShareStatus.UNKNOWN, AlbumShareStatus.SUCCESS) }
                Text((record.title.ifBlank { "제목 없는 기록" }) + if (already) " · 이미 담음" else "", Modifier.fillMaxWidth().clickable(enabled = !busy && storageError == null && !already && !pending) { shares.start(groupId, record.id, detail?.id) }.padding(vertical = 12.dp))
            }
            if (recordMore) TextButton(onClick = { scope.launch { loadRecords(recordPage + 1) } }, enabled = !busy) { Text("기록 더 보기") }
            if (operations.any { it.groupId == groupId && it.status == AlbumShareStatus.UNKNOWN }) Text("결과 미확인 기록은 앨범 목록에서 먼저 확인해 주세요.")
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        } }, confirmButton = { TextButton(onClick = { pick = false }, enabled = !busy) { Text("닫기") } })
    acknowledge?.let { operation ->
        AlertDialog(onDismissRequest = { acknowledge = null }, title = { Text("공유 작업을 종료할까요?") },
            text = { Text("서버에서 이미 앨범이 만들어졌을 수 있어요. 목록을 확인한 뒤 종료해 주세요. 종료해도 서버의 앨범은 삭제되지 않아요.") },
            confirmButton = { TextButton(onClick = { shares.acknowledge(operation); acknowledge = null }) { Text("목록 확인 완료 · 작업 종료") } },
            dismissButton = { TextButton(onClick = { acknowledge = null }) { Text("돌아가기") } })
    }
    if (rename) AlertDialog(onDismissRequest = { if (!busy) rename = false }, title = { Text("앨범 이름 변경") },
        text = { OutlinedTextField(name, { name = it }, enabled = !busy) },
        confirmButton = { TextButton(enabled = !busy && name.isNotBlank(), onClick = { scope.launch {
            val id = detail?.id ?: return@launch
            loading = true; error = null
            try { albums.update(groupId, id, name = name.trim()).onSuccess { detail = it; rename = false }.onFailure { error = "이름을 변경하지 못했어요. 다시 시도해 주세요." } } finally { loading = false }
        } }) { Text("저장") } }, dismissButton = { TextButton(onClick = { rename = false }, enabled = !busy) { Text("취소") } })
}
