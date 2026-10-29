package com.mapmory.shared.domain.group

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Instant

class GroupDomainTest {
    private val now = Instant.parse("2026-10-23T00:00:00Z")
    private val alice = social("alice", "김민수")
    private val bob = social("bob", "김민수")
    private val guest = GroupActor(MemberId("guest"), MemberKind.GUEST, null)
    private val groupId = GroupId("group")

    @Test
    fun `creator is the first member and a guest cannot create`() {
        val group = group()
        assertEquals(listOf(GroupMembership(alice.id)), group.memberships)
        violation(GroupRuleViolation.SocialLoginRequired) {
            TravelGroup.create(groupId, GroupName("여행"), guest)
        }
    }

    @Test
    fun `blank names are rejected without inventing a length or uniqueness policy`() {
        violation(GroupRuleViolation.BlankGroupName) { GroupName(" \n ") }
        val name = GroupName("아주 긴 여행 이름".repeat(40))
        assertEquals(name, group().rename(alice.id, name).name)
    }

    @Test
    fun `same social name does not identify the same member`() {
        val original = group()
        val joined = original.accept(invite(original), bob, now)
        assertEquals(2, joined.memberCount)
        assertTrue(joined.contains(alice.id))
        assertTrue(joined.contains(bob.id))
        assertEquals(1, original.memberCount)
    }

    @Test
    fun `ordinary member can rename and invite without an admin role`() {
        val group = joinedGroup()
        assertEquals("새 이름", group.rename(bob.id, GroupName("새 이름")).name.value)
        assertEquals(bob.id, invite(group, bob.id).issuedBy)
    }

    @Test
    fun `outsider cannot rename or issue an invite`() {
        violation(GroupRuleViolation.NotGroupMember) { group().rename(bob.id, GroupName("이름")) }
        violation(GroupRuleViolation.NotGroupMember) { invite(group(), bob.id) }
    }

    @Test
    fun `guest cannot join even with an active invitation`() {
        val group = group()
        violation(GroupRuleViolation.SocialLoginRequired) { group.accept(invite(group), guest, now) }
    }

    @Test
    fun `invitation is active from issue until exactly 24 hours`() {
        val invitation = invite(group())
        assertEquals(InvitationStatus.NOT_YET_VALID, invitation.statusAt(now - 1.nanoseconds))
        assertEquals(InvitationStatus.ACTIVE, invitation.statusAt(now))
        assertEquals(InvitationStatus.ACTIVE, invitation.statusAt(now + 1.days - 1.nanoseconds))
        assertEquals(InvitationStatus.EXPIRED, invitation.statusAt(now + 1.days))
        assertEquals(InvitationStatus.EXPIRED, invitation.statusAt(now + 2.days))
    }

    @Test
    fun `expired or future invitation cannot add a member`() {
        val group = group()
        violation(GroupRuleViolation.InvitationExpired) { group.accept(invite(group), bob, now + 1.days) }
        violation(GroupRuleViolation.InvitationNotActive) { group.accept(invite(group), bob, now - 1.nanoseconds) }
        assertEquals(1, group.memberCount)
    }

    @Test
    fun `invitation for another group cannot be accepted`() {
        val other = TravelGroup.create(GroupId("other"), GroupName("다른 그룹"), alice)
        violation(GroupRuleViolation.DifferentGroup) { other.accept(invite(group()), bob, now) }
    }

    @Test
    fun `one invite can admit several members and repeated acceptance is explicit`() {
        val group = group()
        val invitation = invite(group)
        val joined = group.accept(invitation, bob, now).accept(invitation, social("charlie"), now)
        assertEquals(3, joined.memberCount)
        violation(GroupRuleViolation.AlreadyGroupMember) { joined.accept(invitation, bob, now) }
    }

    @Test
    fun `30th member can join but 31st cannot and existing membership is distinguishable`() {
        val almostFull = TravelGroup.restore(groupId, GroupName("여행"),
            (1..29).map { GroupMembership(MemberId("member-$it")) })
        val invitation = invite(almostFull, MemberId("member-1"))
        val full = almostFull.accept(invitation, bob, now)
        assertEquals(30, full.memberCount)
        violation(GroupRuleViolation.GroupFull) { full.accept(invitation, alice, now) }
        violation(GroupRuleViolation.AlreadyGroupMember) { full.accept(invitation, bob, now) }
    }

    @Test
    fun `restoration rejects duplicate or over-capacity membership snapshots`() {
        violation(GroupRuleViolation.InvalidSnapshot) {
            TravelGroup.restore(groupId, GroupName("여행"), listOf(GroupMembership(alice.id), GroupMembership(alice.id)))
        }
        violation(GroupRuleViolation.GroupFull) {
            TravelGroup.restore(groupId, GroupName("여행"), (1..31).map { GroupMembership(MemberId("$it")) })
        }
    }

    @Test
    fun `membership snapshot is protected from caller mutation`() {
        val members = mutableListOf(GroupMembership(alice.id), GroupMembership(bob.id))
        val group = TravelGroup.restore(groupId, GroupName("여행"), members)
        members.clear()
        assertEquals(2, group.memberCount)
        (group.memberships as MutableList).clear()
        assertEquals(2, group.memberCount)
    }

    @Test
    fun `leaving removes membership and last departure does not invent a deletion policy`() {
        val remaining = group().leave(alice.id)
        assertEquals(groupId, remaining.id)
        assertEquals(0, remaining.memberCount)
        violation(GroupRuleViolation.NotGroupMember) { remaining.leave(alice.id) }
    }

    @Test
    fun `code enforces six characters but does not assume numeric-only alphabet`() {
        assertEquals("A1B2C3", InvitationCode("A1B2C3").value)
        assertFailsWith<IllegalArgumentException> { InvitationCode("12345") }
        assertFailsWith<IllegalArgumentException> { InvitationCode("12 456") }
    }

    @Test
    fun `invalid restored invitation lifetime is rejected`() {
        violation(GroupRuleViolation.InvalidSnapshot) {
            GroupInvitation.restore(groupId, alice.id, InvitationToken("token"), InvitationCode("123456"), now, now + 2.days)
        }
    }

    @Test
    fun `album links each members own record without changing the original album`() {
        val group = joinedGroup()
        val original = album(group)
        val shared = original.share(group, bob.id, record(2, bob.id))
        assertEquals(1, original.links.size)
        assertEquals(listOf(RecordId(1), RecordId(2)), shared.links.map { it.record.recordId })
        assertEquals(setOf(alice.id, bob.id), shared.contributorIds)
    }

    @Test
    fun `only a current member can share a record they own`() {
        val group = group()
        violation(GroupRuleViolation.NotGroupMember) { album(group).share(group, bob.id, record(2, bob.id)) }
        violation(GroupRuleViolation.NotRecordOwner) { album(group).share(group, alice.id, record(2, bob.id)) }
        violation(GroupRuleViolation.NotRecordOwner) {
            TravelAlbum.open(AlbumId("album"), joinedGroup(), bob.id, record(1, alice.id))
        }
    }

    @Test
    fun `duplicate record link in the same album is rejected`() {
        val group = group()
        violation(GroupRuleViolation.RecordAlreadyShared) { album(group).share(group, alice.id, record(1, alice.id)) }
        violation(GroupRuleViolation.InvalidSnapshot) {
            TravelAlbum.restore(AlbumId("album"), groupId,
                listOf(AlbumRecordLink(record(1, alice.id)), AlbumRecordLink(record(1, bob.id))))
        }
    }

    @Test
    fun `membership departure does not mutate album projections and rejoining can share`() {
        val group = joinedGroup()
        val original = album(group)
        val remaining = group.leave(alice.id)
        assertFalse(remaining.contains(alice.id))
        assertEquals(1, original.links.size) // Server response replaces this projection after cleanup.
        violation(GroupRuleViolation.NotGroupMember) {
            original.share(remaining, alice.id, record(2, alice.id))
        }
        val rejoined = remaining.accept(invite(remaining, bob.id), alice, now)
        assertEquals(2, original.share(rejoined, alice.id, record(2, alice.id)).links.size)
    }

    @Test
    fun `another groups membership cannot authorize sharing`() {
        val other = TravelGroup.create(GroupId("other"), GroupName("다른 그룹"), alice)
        violation(GroupRuleViolation.DifferentGroup) { album(group()).share(other, alice.id, record(2, alice.id)) }
    }

    @Test
    fun `record link snapshot is protected from mutation`() {
        val links = mutableListOf(AlbumRecordLink(record(1, alice.id)), AlbumRecordLink(record(2, bob.id)))
        val album = TravelAlbum.restore(AlbumId("album"), groupId, links)
        links.clear()
        (album.links as MutableList).clear()
        assertEquals(2, album.links.size)
    }

    private fun social(id: String, name: String = id) = GroupActor(MemberId(id), MemberKind.SOCIAL, name)
    private fun group() = TravelGroup.create(groupId, GroupName("함께 여행"), alice)
    private fun joinedGroup(): TravelGroup = group().let { it.accept(invite(it), bob, now) }
    private fun record(id: Long, owner: MemberId) = OwnedRecordReference(RecordId(id), owner)
    private fun album(group: TravelGroup) = TravelAlbum.open(AlbumId("album"), group, alice.id, record(1, alice.id))
    private fun invite(group: TravelGroup, issuer: MemberId = alice.id) =
        GroupInvitation.issue(group, issuer, InvitationToken("token"), InvitationCode("123456"), now)

    private fun violation(expected: GroupRuleViolation, block: () -> Unit) {
        assertEquals(expected, assertFailsWith<GroupRuleException>(block = block).violation)
    }
}
