package com.mapmory.shared.presentation.group

internal sealed interface GroupListUiState {
    data object Loading : GroupListUiState
    data class Content(val groups: List<GroupSummary> = emptyList()) : GroupListUiState
    data class Error(val message: String) : GroupListUiState
}

internal data class GroupSummary(
    val id: String,
    val name: String,
    val memberCount: Int,
    val albums: List<GroupAlbum> = emptyList(),
    val albumCount: Int = albums.size,
    val coverUri: String? = null,
)

internal data class GroupAlbum(
    val id: String,
    val name: String,
    val place: String,
    val date: String,
    val photos: List<GroupPhoto> = emptyList(),
    val photoCount: Int = photos.size,
    val contributorNames: List<String>? = null,
) {
    val contributors: List<String> get() = contributorNames ?: photos.distinctBy { it.uploaderId }.map { it.uploaderName }
}

// Group photos reference a member's record; they are not copies of a shared editable record.
internal data class GroupPhoto(
    val id: String,
    val recordId: Long,
    val uploaderId: String,
    val uploaderName: String, // Display name supplied by social login.
    val imageUri: String? = null,
)

internal val previewGroup = GroupSummary(
    id = "preview-group",
    name = "제주에서의 우리",
    memberCount = 4,
    albums = listOf(
        GroupAlbum("jeju", "제주에서 함께한 주말", "협재해수욕장", "2026.03.01 – 03.04",
            listOf(GroupPhoto("1", 1, "member-1", "성우"), GroupPhoto("2", 2, "member-2", "민지"),
                GroupPhoto("3", 1, "member-1", "성우"), GroupPhoto("4", 3, "member-3", "지우"))),
        GroupAlbum("cafe", "바다 앞 카페", "애월", "2026.03.04",
            listOf(GroupPhoto("5", 4, "member-2", "민지"))),
    ),
)

// Explicit complete personal records for the preview, independent of album projections.
internal val previewRecords = listOf(
    com.mapmory.shared.domain.model.RecordPhotos(1, listOf("1", "3")),
    com.mapmory.shared.domain.model.RecordPhotos(2, listOf("2")),
    com.mapmory.shared.domain.model.RecordPhotos(3, listOf("4")),
    com.mapmory.shared.domain.model.RecordPhotos(4, listOf("5")),
)

internal val previewOwnRecordIds = setOf(1L)
