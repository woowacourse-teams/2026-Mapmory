package com.mapmory.backend.group.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;

import com.mapmory.backend.common.exception.BusinessException;
import com.mapmory.backend.group.exception.GroupErrorCode;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class TravelGroupTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 22, 12, 0);

    @Test
    void 멤버가_나가면_인원이_줄고_방장은_그대로다() {
        TravelGroup group = TravelGroup.create("도쿄 친구들", 1L, NOW);
        group.join(2L, NOW.plusMinutes(1));
        group.join(3L, NOW.plusMinutes(2));

        group.leave(2L);

        assertThat(group.hasMember(2L)).isFalse();
        assertThat(group.getMemberCount()).isEqualTo(2);
        assertThat(group.getOwnerMemberId()).isEqualTo(1L);
    }

    @Test
    void 방장이_나가면_가장_먼저_들어온_멤버가_방장이_된다() {
        TravelGroup group = TravelGroup.create("도쿄 친구들", 1L, NOW);
        group.join(3L, NOW.plusMinutes(1));
        group.join(2L, NOW.plusMinutes(2));

        group.leave(1L);

        assertThat(group.getOwnerMemberId()).isEqualTo(3L);
        assertThat(group.getMemberCount()).isEqualTo(2);
    }

    @Test
    void 혼자_남은_멤버는_나가지_않고_그룹을_지운다() {
        TravelGroup group = TravelGroup.create("도쿄 친구들", 1L, NOW);

        assertThat(group.isOnlyMember(1L)).isTrue();
        assertThatThrownBy(() -> group.leave(1L)).isInstanceOf(IllegalStateException.class);
        group.join(2L, NOW.plusMinutes(1));
        assertThat(group.isOnlyMember(1L)).isFalse();
    }

    @Test
    void 올린_사람을_뺀_멤버들에게_알린다() {
        TravelGroup group = TravelGroup.create("도쿄 친구들", 1L, NOW);
        group.join(2L, NOW.plusMinutes(1));
        group.join(3L, NOW.plusMinutes(2));

        assertThat(group.otherMemberIds(2L)).containsExactly(1L, 3L);
        assertThat(TravelGroup.create("혼자", 1L, NOW).otherMemberIds(1L)).isEmpty();
    }

    @Test
    void 만든_사람이_첫_멤버이자_방장이다() {
        TravelGroup group = TravelGroup.create("도쿄 친구들", 1L, NOW);

        assertThat(group.getOwnerMemberId()).isEqualTo(1L);
        assertThat(group.getMembers())
                .extracting(GroupMember::getMemberId, GroupMember::getJoinedAt)
                .containsExactly(tuple(1L, NOW));
        assertThat(group.hasMember(1L)).isTrue();
        assertThat(group.hasMember(2L)).isFalse();
        assertThat(group.getMemberCount()).isEqualTo(1);
    }

    @Test
    void 이름_앞뒤_공백은_지운다() {
        assertThat(TravelGroup.create("  도쿄 친구들 ", 1L, NOW).getName()).isEqualTo("도쿄 친구들");
    }

    @Test
    void 이름은_30자까지_된다() {
        assertThat(TravelGroup.create("가".repeat(30), 1L, NOW).getName()).hasSize(30);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void 빈_이름은_거절한다(String name) {
        assertThatThrownBy(() -> TravelGroup.create(name, 1L, NOW))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(GroupErrorCode.INVALID_GROUP_NAME);
    }

    @Test
    void 이름이_30자를_넘으면_거절한다() {
        assertThatThrownBy(() -> TravelGroup.create("가".repeat(31), 1L, NOW))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(GroupErrorCode.INVALID_GROUP_NAME);
    }

    @Test
    void 이름을_바꾸면_앞뒤_공백을_지워_저장한다() {
        TravelGroup group = TravelGroup.create("도쿄 친구들", 1L, NOW);

        group.rename("  제주 가족 ");

        assertThat(group.getName()).isEqualTo("제주 가족");
    }

    @Test
    void 규칙에_맞지_않는_이름으로는_바꾸지_않는다() {
        TravelGroup group = TravelGroup.create("도쿄 친구들", 1L, NOW);

        assertThatThrownBy(() -> group.rename("가".repeat(31)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(GroupErrorCode.INVALID_GROUP_NAME);
        assertThat(group.getName()).isEqualTo("도쿄 친구들");
    }

    @Test
    void 초대를_수락하면_들어온_시각과_함께_멤버가_된다() {
        TravelGroup group = TravelGroup.create("도쿄 친구들", 1L, NOW);

        group.join(2L, NOW.plusHours(1));

        assertThat(group.getMembers())
                .extracting(GroupMember::getMemberId, GroupMember::getJoinedAt)
                .containsExactly(tuple(1L, NOW), tuple(2L, NOW.plusHours(1)));
        assertThat(group.getMemberCount()).isEqualTo(2);
        assertThat(group.getOwnerMemberId()).isEqualTo(1L);
    }

    @Test
    void 이미_멤버면_다시_넣지_않는다() {
        TravelGroup group = TravelGroup.create("도쿄 친구들", 1L, NOW);

        group.join(1L, NOW.plusHours(1));

        assertThat(group.getMembers()).hasSize(1);
        assertThat(group.getMemberCount()).isEqualTo(1);
    }

    @Test
    void 서른_명이면_더_들어올_수_없다() {
        TravelGroup group = TravelGroup.create("도쿄 친구들", 1L, NOW);
        for (long memberId = 2; memberId <= 30; memberId++) {
            group.join(memberId, NOW);
        }

        assertThatThrownBy(() -> group.join(31L, NOW))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(GroupErrorCode.GROUP_FULL);
        assertThat(group.getMembers()).hasSize(30);
        assertThat(group.getMemberCount()).isEqualTo(30);
    }

    @Test
    void 서른_명이어도_이미_멤버인_사람의_수락은_통과한다() {
        TravelGroup group = TravelGroup.create("도쿄 친구들", 1L, NOW);
        for (long memberId = 2; memberId <= 30; memberId++) {
            group.join(memberId, NOW);
        }

        group.join(15L, NOW);

        assertThat(group.getMembers()).hasSize(30);
    }
}
