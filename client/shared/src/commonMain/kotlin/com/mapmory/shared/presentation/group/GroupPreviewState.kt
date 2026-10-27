package com.mapmory.shared.presentation.group

import com.mapmory.shared.analytics.MapmoryAnalytics
import com.mapmory.shared.analytics.NoOpMapmoryAnalytics
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.Saver
import com.mapmory.shared.domain.model.RecordPhotoRemoval
import com.mapmory.shared.domain.model.RecordPhotos
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
internal enum class GroupSheet {
    CREATE, JOIN, INVITE, PHOTOS, JOIN_PREVIEW, JOINED, INVITE_READY, EXPIRED, FULL,
    PLACE, RECORDS, PICK, SAVING, FAILED, UNKNOWN, DONE, ADD,
}

@Serializable
internal sealed interface GroupSaveTarget {
    val groupId: String
    @Serializable data class NewAlbum(override val groupId: String) : GroupSaveTarget
    @Serializable data class ExistingAlbum(override val groupId: String, val albumId: String) : GroupSaveTarget
}

@Serializable
internal sealed interface GroupSaveInput {
    @Serializable class Photos private constructor(private val snapshot: List<String>) : GroupSaveInput {
        val ids: List<String> get() = snapshot.toList()
        init { require(snapshot.isNotEmpty() && snapshot.all { it.isNotBlank() } && snapshot.distinct().size == snapshot.size) }
        companion object { fun of(ids: List<String>) = Photos(ids.toList()) }
    }
    @Serializable data class Record(val id: Long) : GroupSaveInput
}

@Serializable
internal enum class GroupSaveStage { UPLOAD, RECORD, LINK, DONE }

/** A preview workflow; network completion must come from the real save coordinator later. */
@Serializable
internal class GroupSaveJob private constructor(
    val target: GroupSaveTarget,
    val input: GroupSaveInput,
    val stage: GroupSaveStage,
) {
    init {
        require(target.groupId.isNotBlank())
        require(target !is GroupSaveTarget.ExistingAlbum || target.albumId.isNotBlank())
        require(input !is GroupSaveInput.Record || stage in setOf(GroupSaveStage.LINK, GroupSaveStage.DONE))
    }

    fun advance(): GroupSaveJob {
        check(stage != GroupSaveStage.DONE)
        val next = when (stage) {
            GroupSaveStage.UPLOAD -> GroupSaveStage.RECORD
            GroupSaveStage.RECORD -> GroupSaveStage.LINK
            GroupSaveStage.LINK -> GroupSaveStage.DONE
            GroupSaveStage.DONE -> error("Already completed")
        }
        return GroupSaveJob(target, input, next)
    }

    companion object {
        fun upload(target: GroupSaveTarget, ids: List<String>) =
            GroupSaveJob(target, GroupSaveInput.Photos.of(ids), GroupSaveStage.UPLOAD)
        fun share(target: GroupSaveTarget, recordId: Long) =
            GroupSaveJob(target, GroupSaveInput.Record(recordId), GroupSaveStage.LINK)
    }
}

@Serializable
private data class GroupDestination(
    val groupId: String? = null,
    val albumId: String? = null,
    val photoIndex: Int? = null,
    val sheet: GroupSheet? = null,
    val error: String? = null,
)

@Serializable
private data class GroupPreviewSnapshot(
    val preview: Boolean = false,
    val emptyPreview: Boolean = false,
    val destination: GroupDestination = GroupDestination(),
    val forward: GroupDestination? = null,
    val photoSession: Int = 0,
    val name: String = "",
    val code: String = "",
    val createdName: String? = null,
    val selectedPhotos: List<String> = emptyList(),
    val deletedPhotoIds: List<String> = emptyList(),
    val pendingDeleteId: String? = null,
    val job: GroupSaveJob? = null,
)

/** Owns preview navigation and actions. It never performs server writes. */
internal class GroupPreviewState private constructor(initial: GroupPreviewSnapshot) {
    constructor() : this(GroupPreviewSnapshot())
    private var data by mutableStateOf(initial)
    var preview: Boolean
        get() = data.preview
        set(value) { data = data.copy(preview = value) }
    var emptyPreview: Boolean
        get() = data.emptyPreview
        set(value) { data = data.copy(emptyPreview = value) }
    var photoSession: Int
        get() = data.photoSession
        set(value) { data = data.copy(photoSession = value) }
    var name: String
        get() = data.name
        set(value) { data = data.copy(name = value) }
    var code: String
        get() = data.code
        set(value) { data = data.copy(code = value) }
    var createdName: String?
        get() = data.createdName
        set(value) { data = data.copy(createdName = value) }
    var selectedPhotos: List<String>
        get() = data.selectedPhotos
        set(value) { data = data.copy(selectedPhotos = value) }
    var pendingDeleteId: String?
        get() = data.pendingDeleteId
        set(value) { data = data.copy(pendingDeleteId = value) }
    var groupId: String?
        get() = data.destination.groupId
        set(value) { data = data.copy(destination = data.destination.copy(groupId = value)) }
    var albumId: String?
        get() = data.destination.albumId
        set(value) { data = data.copy(destination = data.destination.copy(albumId = value)) }
    var photoIndex: Int?
        get() = data.destination.photoIndex
        set(value) { data = data.copy(destination = data.destination.copy(photoIndex = value)) }
    var sheet: GroupSheet?
        get() = data.destination.sheet
        private set(value) { data = data.copy(destination = data.destination.copy(sheet = value)) }
    var error: String?
        get() = data.destination.error
        set(value) { data = data.copy(destination = data.destination.copy(error = value)) }
    val job: GroupSaveJob? get() = data.job
    val saveStage: GroupSaveStage? get() = job?.stage
    val visiblePreviewGroup: GroupSummary get() = previewGroup.copy(albums = previewGroup.albums.map { album ->
        album.copy(photos = album.photos.filterNot { it.id in data.deletedPhotoIds })
    })
    val records: List<RecordPhotos> get() = previewRecords.mapNotNull { record ->
        val remaining = record.photoIds.filterNot { it in data.deletedPhotoIds }
        remaining.takeIf { it.isNotEmpty() }?.let { RecordPhotos(record.recordId, it) }
    }

    val ownRecords: List<RecordPhotos> get() = records.filter { it.recordId in previewOwnRecordIds }

    fun dismissSheet() { sheet = null; error = null; clearForward() }

    fun clearForward() { data = data.copy(forward = null) }
    fun back(): Boolean = when {
        sheet != null -> { sheet = null; error = null; true }
        photoIndex != null -> { photoIndex = null; true }
        albumId != null -> { albumId = null; true }
        groupId != null -> { groupId = null; true }
        else -> false
    }
    fun gestureBack(): Boolean {
        val previous = data.destination
        return back().also { if (it) data = data.copy(forward = previous) }
    }
    fun gestureForward(): Boolean {
        val previous = data.forward ?: return false
        data = data.copy(destination = previous, forward = null)
        return true
    }
    fun openSheet(value: GroupSheet) {
        if (value in setOf(GroupSheet.SAVING, GroupSheet.FAILED, GroupSheet.UNKNOWN, GroupSheet.DONE)) {
            val active = checkNotNull(job) { "A save requires a target and input" }
            check((value == GroupSheet.DONE) == (active.stage == GroupSaveStage.DONE))
        }
        clearForward(); error = null; sheet = value
    }
    private fun target(): GroupSaveTarget {
        val id = checkNotNull(groupId)
        return albumId?.let { GroupSaveTarget.ExistingAlbum(id, it) } ?: GroupSaveTarget.NewAlbum(id)
    }
    fun startUpload() {
        data = data.copy(job = GroupSaveJob.upload(target(), selectedPhotos))
        openSheet(GroupSheet.SAVING)
    }
    fun shareRecord(recordId: Long) {
        check(ownRecords.any { it.recordId == recordId })
        data = data.copy(job = GroupSaveJob.share(target(), recordId))
        openSheet(GroupSheet.SAVING)
    }
    fun advanceSave() {
        check(sheet == GroupSheet.SAVING)
        val next = checkNotNull(job).advance()
        data = data.copy(job = next)
        if (next.stage == GroupSaveStage.DONE) openSheet(GroupSheet.DONE)
    }
    fun retrySave() {
        check(sheet == GroupSheet.FAILED)
        openSheet(GroupSheet.SAVING) // Retains target, input and failed stage.
    }
    fun showPreviewResult() {
        check(sheet == GroupSheet.UNKNOWN)
        var completed = checkNotNull(job)
        while (completed.stage != GroupSaveStage.DONE) completed = completed.advance()
        data = data.copy(job = completed)
        openSheet(GroupSheet.DONE)
    }
    fun cancelSave() {
        data = data.copy(job = null, selectedPhotos = emptyList())
        sheet = null; clearForward()
    }
    fun showSavedAlbum() {
        val completed = checkNotNull(job)
        check(completed.stage == GroupSaveStage.DONE)
        groupId = completed.target.groupId
        albumId = (completed.target as? GroupSaveTarget.ExistingAlbum)?.albumId ?: previewGroup.albums.first().id
        emptyPreview = false; sheet = null
    }
    fun removalResult(photo: GroupPhoto): RecordPhotoRemoval {
        require(photo.uploaderId == "member-1")
        return records.first { it.recordId == photo.recordId }.remove(photo.id)
    }
    fun deletePendingPhoto() {
        val photo = visiblePreviewGroup.albums.flatMap { it.photos }.firstOrNull {
            it.id == pendingDeleteId && it.uploaderId == "member-1"
        } ?: return
        removalResult(photo) // Validate against the complete personal record before updating projections.
        data = data.copy(deletedPhotoIds = data.deletedPhotoIds + photo.id, pendingDeleteId = null)
        photoIndex = null; clearForward()
    }

    companion object {
        val Saver = saver(NoOpMapmoryAnalytics)

        fun saver(analytics: MapmoryAnalytics): Saver<GroupPreviewState, String> {
            val telemetry = GroupAnalytics(analytics)
            return Saver(
                save = { state -> telemetry.guard("group_state_save", state.analyticsContext()) { Json.encodeToString(state.data) } },
                restore = { encoded -> telemetry.guard("group_state_restore", GroupAnalyticsContext()) {
                    GroupPreviewState(Json.decodeFromString<GroupPreviewSnapshot>(encoded))
                } },
            )
        }
    }
}
