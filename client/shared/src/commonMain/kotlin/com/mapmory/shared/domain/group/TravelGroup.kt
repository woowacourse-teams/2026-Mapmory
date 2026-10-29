package com.mapmory.shared.domain.group

import kotlin.time.Instant

/** Membership is owned by the group; the member account has a separate lifecycle. */
data class GroupMembership(val memberId: MemberId)

/** Immutable membership aggregate. No owner/admin role: every current member can rename/invite. */
class TravelGroup private constructor(
    val id: GroupId,
    val name: GroupName,
    memberships: List<GroupMembership>,
) {
    private val membershipSnapshot = memberships.toList()
    val memberships: List<GroupMembership> get() = membershipSnapshot.toList()
    val memberCount: Int get() = membershipSnapshot.size

    init {
        checkGroupRule(memberCount <= MaxMembers, GroupRuleViolation.GroupFull)
        checkGroupRule(
            membershipSnapshot.map { it.memberId }.distinct().size == memberCount,
            GroupRuleViolation.InvalidSnapshot,
        )
    }

    fun contains(memberId: MemberId): Boolean = membershipSnapshot.any { it.memberId == memberId }

    fun requireMember(memberId: MemberId) {
        checkGroupRule(contains(memberId), GroupRuleViolation.NotGroupMember)
    }

    fun rename(actorId: MemberId, name: GroupName): TravelGroup {
        requireMember(actorId)
        return TravelGroup(id, name, membershipSnapshot)
    }

    fun accept(invitation: GroupInvitation, actor: GroupActor, now: Instant): TravelGroup {
        actor.requireSocial()
        checkGroupRule(invitation.groupId == id, GroupRuleViolation.DifferentGroup)
        invitation.requireActive(now)
        checkGroupRule(!contains(actor.id), GroupRuleViolation.AlreadyGroupMember)
        checkGroupRule(memberCount < MaxMembers, GroupRuleViolation.GroupFull)
        return TravelGroup(id, name, membershipSnapshot + GroupMembership(actor.id))
    }

    /** Album cleanup is part of the server leave transaction; this is membership state only. */
    fun leave(actorId: MemberId): TravelGroup {
        requireMember(actorId)
        return TravelGroup(id, name, membershipSnapshot.filterNot { it.memberId == actorId })
    }

    companion object {
        const val MaxMembers = 30

        fun create(id: GroupId, name: GroupName, creator: GroupActor): TravelGroup {
            creator.requireSocial()
            return TravelGroup(id, name, listOf(GroupMembership(creator.id)))
        }

        /** For a complete, authoritative membership snapshot, not a paginated list. */
        fun restore(id: GroupId, name: GroupName, memberships: List<GroupMembership>): TravelGroup =
            TravelGroup(id, name, memberships)
    }
}
