package com.mapmory.shared.domain.group

import kotlin.jvm.JvmInline

import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

@JvmInline
value class InvitationCode(val value: String) {
    init {
        require(value.length == 6 && value.none(Char::isWhitespace)) { "Invitation code must have six characters" }
    }
    // Numeric-only versus alphanumeric is intentionally not decided here.
}

class InvitationToken(val value: String) {
    init { require(value.isNotBlank()) { "Invitation token must not be blank" } }
    override fun toString(): String = "InvitationToken(***)"
}

enum class InvitationStatus { NOT_YET_VALID, ACTIVE, EXPIRED }

/** Clock and token generation are injected as values, so validity is deterministic and testable. */
class GroupInvitation private constructor(
    val groupId: GroupId,
    val issuedBy: MemberId,
    val token: InvitationToken,
    val code: InvitationCode,
    val issuedAt: Instant,
    val expiresAt: Instant,
) {
    init {
        checkGroupRule(expiresAt == issuedAt + Lifetime, GroupRuleViolation.InvalidSnapshot)
    }

    fun statusAt(now: Instant): InvitationStatus = when {
        now < issuedAt -> InvitationStatus.NOT_YET_VALID
        now >= expiresAt -> InvitationStatus.EXPIRED
        else -> InvitationStatus.ACTIVE
    }

    internal fun requireActive(now: Instant) {
        when (statusAt(now)) {
            InvitationStatus.NOT_YET_VALID -> throw GroupRuleException(GroupRuleViolation.InvitationNotActive)
            InvitationStatus.EXPIRED -> throw GroupRuleException(GroupRuleViolation.InvitationExpired)
            InvitationStatus.ACTIVE -> Unit
        }
    }

    companion object {
        val Lifetime = 1.days

        fun issue(
            group: TravelGroup,
            issuerId: MemberId,
            token: InvitationToken,
            code: InvitationCode,
            now: Instant,
        ): GroupInvitation {
            group.requireMember(issuerId)
            return GroupInvitation(group.id, issuerId, token, code, now, now + Lifetime)
        }

        fun restore(
            groupId: GroupId,
            issuedBy: MemberId,
            token: InvitationToken,
            code: InvitationCode,
            issuedAt: Instant,
            expiresAt: Instant,
        ): GroupInvitation = GroupInvitation(groupId, issuedBy, token, code, issuedAt, expiresAt)
    }
}
