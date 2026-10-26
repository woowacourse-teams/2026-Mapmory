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
    void 만든_사람이_첫_멤버이자_방장이다() {
        TravelGroup group = TravelGroup.create("도쿄 친구들", 1L, NOW);

        assertThat(group.getOwnerMemberId()).isEqualTo(1L);
        assertThat(group.getMembers())
                .extracting(GroupMember::getMemberId, GroupMember::getJoinedAt)
                .containsExactly(tuple(1L, NOW));
        assertThat(group.hasMember(1L)).isTrue();
        assertThat(group.hasMember(2L)).isFalse();
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
}
