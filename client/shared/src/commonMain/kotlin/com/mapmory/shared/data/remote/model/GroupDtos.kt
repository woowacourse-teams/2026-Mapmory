package com.mapmory.shared.data.remote.model

import com.mapmory.shared.domain.group.GroupDetail
import com.mapmory.shared.domain.group.GroupInvitationPreview
import com.mapmory.shared.domain.group.GroupMemberView
import com.mapmory.shared.domain.group.GroupSummary
import com.mapmory.shared.domain.group.IssuedGroupInvitation
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable

@Serializable
internal data class GroupNameRequestDto(val name: String)

@Serializable
internal data class GroupSummaryDto(val id: Long, val name: String, val memberCount: Long, val albumCount: Long)

@Serializable
internal data class GroupDetailDto(val id: Long, val name: String, val members: List<GroupMemberDto>)

@Serializable
internal data class GroupMemberDto(val nickname: String, val owner: Boolean, val me: Boolean)

@Serializable
internal data class GroupInvitationDto(val token: String, val code: String, val expiresAt: String) {
    override fun toString(): String = "GroupInvitationDto(***)"
}

@Serializable
internal data class GroupInvitationPreviewDto(val groupName: String, val memberCount: Int, val alreadyMember: Boolean)

internal fun GroupSummaryDto.toDomain() = GroupSummary(id, name, memberCount, albumCount)
internal fun GroupDetailDto.toDomain() = GroupDetail(id, name, members.map { GroupMemberView(it.nickname, it.owner, it.me) })
internal fun GroupInvitationDto.toDomain() = IssuedGroupInvitation(token, code, LocalDateTime.parse(expiresAt))
internal fun GroupInvitationPreviewDto.toDomain() = GroupInvitationPreview(groupName, memberCount, alreadyMember)
