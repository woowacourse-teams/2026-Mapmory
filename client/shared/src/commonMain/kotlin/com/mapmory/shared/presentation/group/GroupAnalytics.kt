package com.mapmory.shared.presentation.group

import com.mapmory.shared.analytics.MapmoryAnalytics
import com.mapmory.shared.analytics.MapmoryAnalyticsEvent
import com.mapmory.shared.analytics.emit
import com.mapmory.shared.analytics.reportFailure
import com.mapmory.shared.domain.group.GroupRuleException
import kotlinx.coroutines.CancellationException

/** Finite action vocabulary: never use a label, ID, input value or exception message as an event. */
internal enum class GroupAction {
    PREVIEW_TOGGLED, EMPTY_PREVIEW_TOGGLED,
    CREATE_OPENED, CREATE_SUBMITTED, NAME_EDITED,
    JOIN_OPENED, CODE_EDITED, INVITATION_CHECKED, JOIN_CONFIRMED, JOINED_GROUP_OPENED,
    INVITE_OPENED, INVITE_GENERATED, EXPIRED_SIMULATED, FULL_SIMULATED, INVITATION_RETURNED,
    GROUP_OPENED, LIST_SCROLLED, HOME_SCROLLED, ALBUM_SCROLLED, SHEET_SCROLLED, ALBUM_OPENED, ALBUM_ADD_OPENED, NEW_ALBUM_SELECTED, PLACE_SELECTED,
    RECORD_PICKER_OPENED, RECORD_SELECTED, PHOTOS_ADD_OPENED, PHOTO_PICKER_OPENED,
    PHOTO_SELECTED, PHOTO_DESELECTED, PHOTO_SAVE_SUBMITTED, PHOTO_OPENED, PHOTO_PAGE_CHANGED,
    PHOTO_CLOSED, PHOTO_DELETE_OPENED, PHOTO_DELETE_CONFIRMED, PHOTO_DELETE_CANCELLED, PHOTO_DELETE_DISMISSED,
    SAVE_ADVANCED, FAILURE_SIMULATED, UNKNOWN_SIMULATED, SAVE_CLOSED, SAVE_RETRIED,
    RESULT_CHECKED, SAVE_DISCARDED, SAVED_ALBUM_OPENED,
    SHEET_DISMISSED, HEADER_BACK, SYSTEM_BACK, EDGE_BACK, EDGE_FORWARD, EDGE_BACK_CANCELLED, EDGE_FORWARD_CANCELLED, LIST_RETRIED,
}

/** All fields are structural metadata. No names, codes, paths, record/member IDs or tokens. */
internal data class GroupAnalyticsContext(
    val screen: String = "group_list",
    val overlay: String = "none",
    val executionMode: String = "unavailable",
    val stage: String = "none",
    val targetType: String = "none",
    val inputType: String = "none",
    val selectedCount: Int = 0,
    val recordCount: Int = 0,
    val photoCount: Int = 0,
) {
    fun parameters(): Map<String, String> = mapOf(
        "feature" to "group", "screen" to screen, "overlay" to overlay,
        "execution_mode" to executionMode, "data_origin" to "sample",
        "stage" to stage, "target_type" to targetType, "input_type" to inputType,
        "selected_count" to selectedCount.toString(), "record_count" to recordCount.toString(),
        "photo_count" to photoCount.toString(),
    )
}

internal fun GroupPreviewState.analyticsContext() = GroupAnalyticsContext(
    screen = when {
        photoIndex != null -> "group_photo"
        albumId != null -> "group_album"
        groupId != null -> "group_home"
        else -> "group_list"
    },
    overlay = if (pendingDeleteId != null) "delete_confirmation" else sheet?.name?.lowercase() ?: "none",
    executionMode = if (preview) "preview" else "unavailable",
    stage = saveStage?.name?.lowercase() ?: "none",
    targetType = when (job?.target) {
        is GroupSaveTarget.NewAlbum -> "new_album"
        is GroupSaveTarget.ExistingAlbum -> "existing_album"
        null -> "none"
    },
    inputType = when (job?.input) {
        is GroupSaveInput.Photos -> "photos"
        is GroupSaveInput.Record -> "record"
        null -> "none"
    },
    selectedCount = selectedPhotos.size,
    recordCount = ownRecords.size,
    photoCount = ownRecords.sumOf { it.photoIds.size },
)

/** IDs here are local deduplication keys only and never become analytics parameters. */
internal data class GroupViewKey(
    val groupId: String?, val albumId: String?, val photoSession: Int,
    val sheet: GroupSheet?, val pendingDeleteId: String?, val preview: Boolean,
    val stage: GroupSaveStage?,
)

internal fun GroupPreviewState.viewKey() = GroupViewKey(
    groupId, albumId, if (photoIndex != null) photoSession else -1,
    sheet, pendingDeleteId, preview,
    if (sheet in setOf(GroupSheet.SAVING, GroupSheet.FAILED, GroupSheet.UNKNOWN, GroupSheet.DONE)) saveStage else null,
)

internal class GroupAnalytics(private val analytics: MapmoryAnalytics) {
    private var lastViewKey: GroupViewKey? = null
    private var activeAction: GroupAction? = null
    /** Button callbacks are measured at their owner; child buttons/cards do not emit duplicates. */
    fun <T> action(action: GroupAction, context: () -> GroupAnalyticsContext, block: () -> T): T {
        val before = context()
        analytics.emit(MapmoryAnalyticsEvent.GROUP_ACTION, before.parameters() + ("action" to action.name.lowercase()))
        val previousAction = activeAction
        activeAction = action
        try {
            val result = guard("group_action", before, action, block)
            previewOutcome(action, before, context())
            return result
        } finally {
            activeAction = previousAction
        }
    }

    fun viewed(context: GroupAnalyticsContext, key: GroupViewKey) {
        if (lastViewKey == key) return
        lastViewKey = key
        analytics.emit(MapmoryAnalyticsEvent.GROUP_VIEWED, context.parameters())
    }

    fun blocked(context: GroupAnalyticsContext) {
        analytics.emit(MapmoryAnalyticsEvent.GROUP_ACTION_REJECTED, context.parameters() + buildMap {
            put("reason", "service_unavailable")
            activeAction?.let { put("action", it.name.lowercase()) }
        })
    }

    fun <T> guard(
        operation: String, context: GroupAnalyticsContext, action: GroupAction? = null, block: () -> T,
    ): T = try {
        block()
    } catch (error: CancellationException) {
        throw error
    } catch (error: GroupRuleException) {
        analytics.emit(MapmoryAnalyticsEvent.GROUP_ACTION_REJECTED, context.parameters() + buildMap {
            action?.let { put("action", it.name.lowercase()) }
            put("reason", error.violation.name.lowercase())
        })
        throw error
    } catch (error: Exception) {
        analytics.reportFailure(operation, error, context.parameters() + buildMap {
            action?.let { put("action", it.name.lowercase()) }
        })
        throw error
    }

    private fun previewOutcome(action: GroupAction, before: GroupAnalyticsContext, after: GroupAnalyticsContext) {
        if (after.executionMode != "preview") return
        val outcome = when (action) {
            GroupAction.CREATE_SUBMITTED -> "group_created"
            GroupAction.JOIN_CONFIRMED -> "group_joined"
            GroupAction.INVITE_GENERATED -> "invitation_issued"
            GroupAction.PHOTO_SAVE_SUBMITTED, GroupAction.RECORD_SELECTED -> "save_started"
            GroupAction.SAVE_ADVANCED -> if (after.stage == "done") "save_completed" else "save_progressed"
            GroupAction.FAILURE_SIMULATED -> "save_failed"
            GroupAction.UNKNOWN_SIMULATED -> "save_result_unknown"
            GroupAction.SAVE_RETRIED -> "save_retried"
            GroupAction.RESULT_CHECKED -> "save_completed"
            GroupAction.SAVE_DISCARDED -> "save_discarded"
            GroupAction.PHOTO_DELETE_CONFIRMED -> {
                if (after.photoCount >= before.photoCount) return
                if (after.recordCount < before.recordCount) "record_deleted" else "photo_deleted"
            }
            else -> return
        }
        analytics.emit(MapmoryAnalyticsEvent.GROUP_PREVIEW_RESULT, after.parameters() + ("outcome" to outcome))
    }
}
