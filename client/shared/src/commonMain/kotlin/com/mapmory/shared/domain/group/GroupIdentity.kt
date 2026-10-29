package com.mapmory.shared.domain.group

import kotlin.jvm.JvmInline

/** Opaque domain IDs. Their transport representation is decided by the future API adapter. */
@JvmInline
value class MemberId(val value: String) {
    init { require(value.isNotBlank()) { "Member ID must not be blank" } }
}

@JvmInline
value class GroupId(val value: String) {
    init { require(value.isNotBlank()) { "Group ID must not be blank" } }
}

@JvmInline
value class AlbumId(val value: String) {
    init { require(value.isNotBlank()) { "Album ID must not be blank" } }
}

/** Retains the ID type used by the existing TripRecordData. */
@JvmInline
value class RecordId(val value: Long)

@JvmInline
value class GroupName(val value: String) {
    init { checkGroupRule(value.isNotBlank(), GroupRuleViolation.BlankGroupName) }
    // No arbitrary maximum length, uniqueness constraint, or silent truncation.
}

enum class MemberKind { GUEST, SOCIAL }

/** Social-provider display name is presentation data, never an identity key. */
data class GroupActor(
    val id: MemberId,
    val kind: MemberKind,
    val socialName: String?,
) {
    internal fun requireSocial() {
        checkGroupRule(kind == MemberKind.SOCIAL, GroupRuleViolation.SocialLoginRequired)
    }
}

enum class GroupRuleViolation {
    BlankGroupName,
    SocialLoginRequired,
    NotGroupMember,
    AlreadyGroupMember,
    GroupFull,
    DifferentGroup,
    InvitationNotActive,
    InvitationExpired,
    NotRecordOwner,
    RecordAlreadyShared,
    InvalidSnapshot,
}

/** Stable domain reason; UI text and HTTP status mapping belong to outer layers. */
class GroupRuleException(val violation: GroupRuleViolation) : IllegalArgumentException(violation.name)

internal fun checkGroupRule(condition: Boolean, violation: GroupRuleViolation) {
    if (!condition) throw GroupRuleException(violation)
}
