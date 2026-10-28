package com.mapmory.backend.group.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class GroupInvitationTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 26, 12, 0);

    @Test
    void 초대는_1일_뒤에_만료된다() {
        GroupInvitation invitation = GroupInvitation.issue(3L, 1L, "token", "4KD7PZ", NOW);

        assertThat(invitation.getExpiresAt()).isEqualTo(NOW.plusDays(1));
        assertThat(invitation.isExpired(NOW.plusDays(1).minusSeconds(1))).isFalse();
        assertThat(invitation.isExpired(NOW.plusDays(1))).isTrue();
    }

    @Test
    void 만든_사람과_그룹을_기억한다() {
        GroupInvitation invitation = GroupInvitation.issue(3L, 1L, "token", "4KD7PZ", NOW);

        assertThat(invitation.getGroupId()).isEqualTo(3L);
        assertThat(invitation.getInviterMemberId()).isEqualTo(1L);
        assertThat(invitation.getToken()).isEqualTo("token");
        assertThat(invitation.getCode()).isEqualTo("4KD7PZ");
    }
}
