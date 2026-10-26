package com.mapmory.shared.presentation.group

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mapmory.shared.navigation.LocalNavigationGestureMotion
import com.mapmory.shared.navigation.MapmoryBackHandlerRegistry
import com.mapmory.shared.navigation.PlatformBackHandler
import com.mapmory.shared.presentation.triprecord.screen.TripBottomBar
import com.mapmory.shared.presentation.triprecord.screen.TripBottomTab
import com.mapmory.shared.presentation.triprecord.screen.TripRecordBackground

/** UI integration only. No synthetic group memberships or invite tokens are persisted. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GroupRoute(
    onMapClick: () -> Unit,
    onRecordClick: () -> Unit,
    onProfileClick: () -> Unit,
    onSettingsClick: () -> Unit,
    previewEnabled: Boolean,
    backHandlerRegistry: MapmoryBackHandlerRegistry,
    backHandlerOwnerId: String,
    modifier: Modifier = Modifier,
) {
    val state = rememberSaveable(saver = GroupPreviewState.Saver) { GroupPreviewState() }
    val gestureMotion = LocalNavigationGestureMotion.current
    with(state) {
        val group = if (preview && groupId != null) {
            visiblePreviewGroup.copy(name = createdName ?: previewGroup.name,
                albums = if (emptyPreview) emptyList() else visiblePreviewGroup.albums)
        } else null
        val album = group?.albums?.firstOrNull { it.id == albumId }
        val screenStates = rememberSaveableStateHolder()
        val back = rememberUpdatedState<() -> Boolean> { run { state.back() } }
        val gestureBack = rememberUpdatedState<() -> Boolean> {
            run { state.gestureBack() }
        }
        val gestureForward = rememberUpdatedState<() -> Boolean> {
            run { state.gestureForward() }
        }
        DisposableEffect(backHandlerRegistry, backHandlerOwnerId) {
            val registration = backHandlerRegistry.register(backHandlerOwnerId,
                gestureBack = { gestureBack.value() }, gestureForward = { gestureForward.value() },
            ) { clearForward(); back.value() }
            onDispose { backHandlerRegistry.unregister(registration) }
        }
        PlatformBackHandler(enabled = sheet != null || groupId != null) { clearForward(); back.value() }
        fun previewAction(action: () -> Unit) {
            if (preview) action() else { error = ServerPending }
        }

        TripRecordBackground(modifier = modifier) {
            if (album != null && photoIndex != null && album.photos.isNotEmpty()) {
                screenStates.SaveableStateProvider("photo/${group.id}/${album.id}/$photoSession") {
                    GroupPhotoScreen(album, photoIndex!!,
                        onBack = { run { clearForward(); photoIndex = null } },
                        currentMemberId = "member-1",
                        onDelete = { run { pendingDeleteId = it.id } },
                        onPageChanged = { _, _ -> run {} },
                    )
                }
            } else Column(Modifier.fillMaxSize()) {
                if (previewEnabled) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        TextButton(onClick = {
                            run {
                                clearForward(); preview = !preview; groupId = null; albumId = null; photoIndex = null
                            }
                        }) { Text(if (preview) "미리보기 종료" else "그룹 UI 미리보기") }
                        if (preview) TextButton(onClick = {
                            run { clearForward(); emptyPreview = !emptyPreview; albumId = null }
                        }) { Text(if (emptyPreview) "앨범 있는 상태" else "앨범 없는 상태") }
                    }
                }
                if (!preview) Text("그룹 기능을 준비하고 있어요", Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelMedium)
                if (preview) Text("샘플 데이터 · 실제 그룹이 아닙니다", Modifier.padding(horizontal = 24.dp),
                    style = MaterialTheme.typography.labelSmall)
                Box(Modifier.weight(1f)) {
                    screenStates.SaveableStateProvider("screen/${groupId.orEmpty()}/${albumId.orEmpty()}") {
                        when {
                            album != null -> GroupAlbumScreen(
                                album = album,
                                onBack = { run { clearForward(); albumId = null } },
                                onScrolled = { run {} },
                                onAddPhotos = { run { openSheet(GroupSheet.PHOTOS) } },
                                onPhoto = { run { clearForward(); photoSession++; photoIndex = it } },
                            )
                            group != null -> GroupHomeScreen(
                                group = group,
                                onBack = { run { clearForward(); groupId = null } },
                                onScrolled = { run {} },
                                onInvite = { run { openSheet(GroupSheet.INVITE) } },
                                onAdd = { run { openSheet(GroupSheet.ADD) } },
                                onAlbum = { run { clearForward(); albumId = it } },
                            )
                            else -> GroupListScreen(
                                state = GroupListUiState.Content(
                                    if (preview) listOf(visiblePreviewGroup.copy(
                                        name = createdName ?: previewGroup.name,
                                        albums = if (emptyPreview) emptyList() else visiblePreviewGroup.albums,
                                    )) else emptyList(),
                                ),
                                onCreate = { run { openSheet(GroupSheet.CREATE) } },
                                onJoin = { run { openSheet(GroupSheet.JOIN) } },
                                onGroup = { run { clearForward(); groupId = it } },
                                onScrolled = { run {} },
                                onRetry = { run {} },
                            )
                        }
                    }
                }
                TripBottomBar(selected = TripBottomTab.GROUP,
                    onMapClick = { run { onMapClick() } },
                    onRecordClick = { run { onRecordClick() } },
                    onProfileClick = { run { onProfileClick() } },
                    onSettingsClick = { run { onSettingsClick() } },
                    onGroupClick = { clearForward(); groupId = null; albumId = null; photoIndex = null })
            }
            val pendingPhoto = visiblePreviewGroup.albums.flatMap { it.photos }
                .firstOrNull { it.id == pendingDeleteId && it.uploaderId == "member-1" }
            if (pendingPhoto != null) {
                val lastPhoto = run {
                    removalResult(pendingPhoto) is com.mapmory.shared.domain.model.RecordPhotoRemoval.RecordDeleted
                }
                AlertDialog(
                    onDismissRequest = { run { pendingDeleteId = null } },
                    title = { Text(if (lastPhoto) "마지막 사진을 삭제할까요?" else "사진을 삭제할까요?") },
                    text = { Text(if (lastPhoto)
                        "이 사진을 삭제하면 여행 기록도 함께 삭제돼요.\n샘플 데이터에만 적용되는 미리보기입니다."
                        else "개인 기록과 이 사진을 공유한 앨범에서도 삭제돼요.\n샘플 데이터에만 적용되는 미리보기입니다.") },
                    confirmButton = { TextButton(onClick = {
                        run { deletePendingPhoto() }
                    }) { Text("삭제") } },
                    dismissButton = { TextButton(onClick = { run { pendingDeleteId = null } }) { Text("취소") } },
                )
            }
            if (sheet != null) ModalBottomSheet(onDismissRequest = { run { clearForward(); dismissSheet(); error = null } }) {
                val scrollState = rememberScrollState()
                GroupScrollObserver(scrollState, { scrollState.isScrollInProgress }) { run {} }
                Column(Modifier.fillMaxWidth().imePadding().verticalScroll(scrollState).padding(horizontal = 24.dp).padding(bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    Text(when (sheet) {
                        GroupSheet.CREATE -> "새 그룹 만들기"
                        GroupSheet.JOIN -> "코드로 참여"
                        GroupSheet.INVITE -> "멤버 초대"
                        GroupSheet.PHOTOS -> "앨범에 사진 추가"
                        GroupSheet.JOIN_PREVIEW -> "초대받은 그룹"
                        GroupSheet.JOINED -> "그룹에 참여했어요"
                        GroupSheet.INVITE_READY -> "초대 코드"
                        GroupSheet.EXPIRED -> "만료된 초대"
                        GroupSheet.FULL -> "그룹 정원 초과"
                        GroupSheet.PLACE -> "앨범의 장소"
                        GroupSheet.RECORDS -> "내 여행 기록 공유"
                        GroupSheet.PICK -> "사진 선택"
                        GroupSheet.SAVING -> "사진 저장 중"
                        GroupSheet.FAILED -> "사진 저장 실패"
                        GroupSheet.UNKNOWN -> "저장 결과 확인 중"
                        GroupSheet.DONE -> "사진을 모았어요"
                        else -> "앨범 추가"
                    }, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    when (sheet) {
                        GroupSheet.CREATE -> {
                            GroupMuted("함께할 사람들에게 보여줄 그룹 이름을 입력해 주세요.")
                            OutlinedTextField(value = name, onValueChange = { value -> run { name = value; error = null } },
                                label = { Text("그룹 이름") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                                isError = error != null)
                            GroupButton("그룹 만들기", { run { previewAction {
                                createdName = name.trim(); emptyPreview = true; groupId = previewGroup.id; dismissSheet()
                            } } }, Modifier.fillMaxWidth(),
                                enabled = name.isNotBlank(), tall = true)
                        }
                        GroupSheet.JOIN -> {
                            GroupMuted("초대받은 6자리 코드를 입력해 주세요.")
                            OutlinedTextField(value = code, onValueChange = { value ->
                                run {
                                    code = value.filter(Char::isDigit).take(6); error = null
                                }
                            }, label = { Text("초대 코드") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                                isError = error != null)
                            GroupButton("초대 확인", { run { previewAction { openSheet(GroupSheet.JOIN_PREVIEW) } } }, Modifier.fillMaxWidth(),
                                enabled = code.length == 6, tall = true)
                        }
                        GroupSheet.INVITE -> {
                            GroupCard {
                                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(group?.name.orEmpty(), fontWeight = FontWeight.Bold)
                                    GroupMuted("멤버 ${group?.memberCount ?: 0}명 / 최대 30명")
                                }
                            }
                            GroupMuted("링크나 코드로 멤버를 초대해요.\n멤버 누구나 초대할 수 있고, 초대는 하루 동안 유효해요.")
                            GroupButton("초대 링크·코드 만들기", { run { previewAction { openSheet(GroupSheet.INVITE_READY) } } }, Modifier.fillMaxWidth(), tall = true)
                        }
                        GroupSheet.JOIN_PREVIEW -> {
                            Text(previewGroup.name, fontWeight = FontWeight.Bold)
                            GroupMuted("멤버 4명 · 소셜 로그인 이름이 그룹에 표시돼요.")
                            GroupButton("참여하기", { run { groupId = previewGroup.id; emptyPreview = true; openSheet(GroupSheet.JOINED) } }, Modifier.fillMaxWidth())
                            TextButton(onClick = { run { openSheet(GroupSheet.EXPIRED) } }) { Text("만료 상태 미리보기") }
                            TextButton(onClick = { run { openSheet(GroupSheet.FULL) } }) { Text("정원 초과 상태 미리보기") }
                        }
                        GroupSheet.JOINED -> {
                            GroupMuted("샘플 그룹 참여가 완료됐어요.")
                            GroupButton("그룹 보기", { run { dismissSheet() } }, Modifier.fillMaxWidth())
                        }
                        GroupSheet.INVITE_READY -> {
                            Text("284917", fontSize = 32.sp, fontWeight = FontWeight.Bold)
                            GroupMuted("미리보기 전용 코드 · 실제 초대에 사용할 수 없어요.\n발급 후 24시간 동안 유효해요.")
                            TextButton(onClick = { run { openSheet(GroupSheet.EXPIRED) } }) { Text("만료 상태 미리보기") }
                        }
                        GroupSheet.EXPIRED, GroupSheet.FULL -> {
                            GroupMuted(if (sheet == GroupSheet.EXPIRED) "초대가 만료됐어요. 새 초대를 요청해 주세요." else "최대 30명까지 참여할 수 있어요.")
                            GroupButton("돌아가기", { run { openSheet(GroupSheet.JOIN) } }, Modifier.fillMaxWidth())
                        }
                        GroupSheet.PLACE -> {
                            GroupMuted("샘플 장소를 선택해 앨범 만들기를 이어가세요.")
                            GroupCard(onClick = { run { selectedPhotos = emptyList(); openSheet(GroupSheet.PICK) } }) {
                                Text("제주 · 협재해수욕장", Modifier.padding(20.dp))
                            }
                        }
                        GroupSheet.RECORDS -> {
                            GroupMuted("공유해도 내 여행 기록은 그대로 남아요.")
                            ownRecords.forEach { record ->
                                GroupCard(onClick = { run { shareRecord(record.recordId) } }) {
                                    Text("내 여행 기록 ${record.recordId}", Modifier.padding(20.dp))
                                }
                            }
                        }
                        GroupSheet.PICK -> {
                            GroupMuted("샘플 사진 선택 · ${selectedPhotos.size}장 선택됨")
                            previewGroup.albums.first().photos.forEach { photo ->
                                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                    Checkbox(checked = photo.id in selectedPhotos, onCheckedChange = { checked ->
                                        run {
                                            selectedPhotos = if (checked) selectedPhotos + photo.id else selectedPhotos - photo.id
                                        }
                                    })
                                    Text("샘플 사진 ${photo.id}")
                                }
                            }
                            GroupButton("${selectedPhotos.size}장 추가", { run { startUpload() } },
                                Modifier.fillMaxWidth(), enabled = selectedPhotos.isNotEmpty())
                        }
                        GroupSheet.SAVING -> {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            Text(when (saveStage) { GroupSaveStage.UPLOAD -> "사진 업로드 중"; GroupSaveStage.RECORD -> "내 기록 저장 중"; else -> "앨범에 연결 중" })
                            GroupMuted("미리보기에서는 아래 버튼으로 처리 상태를 전환해요.")
                            GroupButton("다음 단계", {
                                run { advanceSave() }
                            }, Modifier.fillMaxWidth())
                            TextButton(onClick = { run { openSheet(GroupSheet.FAILED) } }) { Text("실패 상태 보기") }
                            TextButton(onClick = { run { openSheet(GroupSheet.UNKNOWN) } }) { Text("연결 끊김 상태 보기") }
                            TextButton(onClick = { run { dismissSheet() } }) { Text("닫기") }
                        }
                        GroupSheet.FAILED, GroupSheet.UNKNOWN -> {
                            GroupMuted(if (sheet == GroupSheet.FAILED) when (saveStage) {
                                GroupSaveStage.UPLOAD -> "사진 업로드에 실패했어요. 네트워크 연결 상태를 확인한 후 다시 시도해 주세요. 선택한 사진은 유지돼요."
                                GroupSaveStage.RECORD -> "내 기록을 저장하지 못했어요. 네트워크 연결 상태를 확인한 후 다시 시도해 주세요."
                                else -> "앨범에 연결하지 못했어요. 네트워크 연결 상태를 확인한 후 다시 시도해 주세요."
                            } else "연결이 끊겨 결과를 확인하고 있어요. 중복 저장하지 않도록 결과를 먼저 확인해요.")
                            GroupButton(if (sheet == GroupSheet.FAILED && saveStage == GroupSaveStage.UPLOAD) "실패한 사진 묶음 다시 시도" else if (sheet == GroupSheet.FAILED) "다시 시도" else "결과 확인", {
                                run {
                                    if (sheet == GroupSheet.FAILED) retrySave() else showPreviewResult()
                                }
                            }, Modifier.fillMaxWidth())
                            if (sheet == GroupSheet.FAILED) TextButton(onClick = { run { cancelSave() } }) { Text("선택 취소") }
                        }
                        GroupSheet.DONE -> {
                            GroupMuted("업로드 · 내 기록 저장 · 앨범 연결이 완료된 상태예요.\n실제 저장은 하지 않는 미리보기입니다.")
                            GroupButton("앨범 보기", { run { showSavedAlbum() } }, Modifier.fillMaxWidth())
                        }
                        GroupSheet.PHOTOS -> {
                            GroupMuted("내 사진을 이 앨범에 추가해요.\n다른 멤버가 올린 사진과 함께 볼 수 있어요.")
                            GroupButton("사진 선택", { run { previewAction { selectedPhotos = emptyList(); openSheet(GroupSheet.PICK) } } }, Modifier.fillMaxWidth(), tall = true)
                        }
                        GroupSheet.ADD -> {
                            GroupMuted("새 여행의 사진을 모으거나 내 여행 기록을 공유해 보세요.")
                            GroupButton("새 앨범 만들기", { run { previewAction { openSheet(GroupSheet.PLACE) } } }, Modifier.fillMaxWidth(), tall = true)
                            GroupButton("내 여행 기록 공유", { run { previewAction { openSheet(GroupSheet.RECORDS) } } }, Modifier.fillMaxWidth(), outlined = true)
                        }
                        null -> Unit
                    }
                    if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

private const val ServerPending = "아직 그룹 서비스를 준비 중이에요. 지금은 그룹 생성·참여·사진 공유를 사용할 수 없어요."
