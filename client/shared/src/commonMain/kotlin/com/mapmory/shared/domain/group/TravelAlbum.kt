package com.mapmory.shared.domain.group

/** Ownership projection supplied by the record domain/server, never inferred from the UI name. */
data class OwnedRecordReference(val recordId: RecordId, val ownerId: MemberId)

/** This link owns neither the record nor its photos. No selected-photo subset in the MVP. */
data class AlbumRecordLink(val record: OwnedRecordReference)

/**
 * Separate aggregate from TravelGroup. Membership is checked using an authoritative group snapshot.
 * Metadata derivation (title, dates, first record) awaits a product/API decision.
 */
class TravelAlbum private constructor(
    val id: AlbumId,
    val groupId: GroupId,
    links: List<AlbumRecordLink>,
) {
    private val linkSnapshot = links.toList()
    val links: List<AlbumRecordLink> get() = linkSnapshot.toList()
    val contributorIds: Set<MemberId> get() = linkSnapshot.map { it.record.ownerId }.toSet()
    val isEmpty: Boolean get() = linkSnapshot.isEmpty()

    init {
        checkGroupRule(
            linkSnapshot.map { it.record.recordId }.distinct().size == linkSnapshot.size,
            GroupRuleViolation.InvalidSnapshot,
        )
    }

    fun share(group: TravelGroup, actorId: MemberId, record: OwnedRecordReference): TravelAlbum {
        requireAccess(group, actorId)
        checkGroupRule(record.ownerId == actorId, GroupRuleViolation.NotRecordOwner)
        checkGroupRule(
            linkSnapshot.none { it.record.recordId == record.recordId },
            GroupRuleViolation.RecordAlreadyShared,
        )
        return TravelAlbum(id, groupId, linkSnapshot + AlbumRecordLink(record))
    }

    // User deletion belongs to the personal record operation. After deletion or departure,
    // reload the authoritative album snapshot; no client-side unlink/cleanup command is exposed.

    private fun requireAccess(group: TravelGroup, actorId: MemberId) {
        checkGroupRule(group.id == groupId, GroupRuleViolation.DifferentGroup)
        group.requireMember(actorId)
    }

    companion object {
        fun open(
            id: AlbumId,
            group: TravelGroup,
            actorId: MemberId,
            firstRecord: OwnedRecordReference,
        ): TravelAlbum = TravelAlbum(id, group.id, emptyList()).share(group, actorId, firstRecord)

        /** Empty albums are representable; retain/delete policy is deliberately left to orchestration. */
        fun restore(id: AlbumId, groupId: GroupId, links: List<AlbumRecordLink>): TravelAlbum =
            TravelAlbum(id, groupId, links)
    }
}
