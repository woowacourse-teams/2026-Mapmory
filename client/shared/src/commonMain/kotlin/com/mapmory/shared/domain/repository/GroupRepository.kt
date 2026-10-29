package com.mapmory.shared.domain.repository

import com.mapmory.shared.domain.group.GroupDetail
import com.mapmory.shared.domain.group.GroupInvitationPreview
import com.mapmory.shared.domain.group.GroupSummary
import com.mapmory.shared.domain.group.IssuedGroupInvitation

interface GroupRepository {
    suspend fun groups(): Result<List<GroupSummary>>
    suspend fun createGroup(name: String): Result<GroupDetail>
    suspend fun getGroup(groupId: Long): Result<GroupDetail>
    suspend fun renameGroup(groupId: Long, name: String): Result<GroupDetail>
    suspend fun createInvitation(groupId: Long): Result<IssuedGroupInvitation>

    /** Guest sessions may preview invitations; accepting requires a social account. */
    suspend fun previewInvitation(tokenOrCode: String): Result<GroupInvitationPreview>
    suspend fun acceptInvitation(tokenOrCode: String): Result<GroupDetail>
}
