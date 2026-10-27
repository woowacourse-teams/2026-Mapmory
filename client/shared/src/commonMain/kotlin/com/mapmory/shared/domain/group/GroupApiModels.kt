package com.mapmory.shared.domain.group

import kotlinx.datetime.LocalDateTime

data class GroupSummary(val id: Long, val name: String, val memberCount: Long, val albumCount: Long)

/** Server member views omit IDs, so they cannot restore a TravelGroup aggregate. */
data class GroupDetail(val id: Long, val name: String, val members: List<GroupMemberView>)

data class GroupMemberView(val nickname: String, val owner: Boolean, val me: Boolean)

/** The server returns a local date-time without an offset; do not interpret it as UTC. */
data class IssuedGroupInvitation(val token: String, val code: String, val expiresAt: LocalDateTime) {
    override fun toString(): String = "IssuedGroupInvitation(token=***, code=***, expiresAt=$expiresAt)"
}

data class GroupInvitationPreview(val groupName: String, val memberCount: Int, val alreadyMember: Boolean)
