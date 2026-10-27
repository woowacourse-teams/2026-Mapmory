package com.mapmory.backend.group.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;

import com.mapmory.backend.common.exception.BusinessException;
import com.mapmory.backend.group.exception.GroupErrorCode;
import com.mapmory.backend.region.domain.Region;
import com.mapmory.backend.region.domain.RegionType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class AlbumTest {

    private static final Region JAPAN = Region.of(null, null, "JP", "일본", RegionType.COUNTRY);

    @Test
    void 따로_올리면_연_사람의_기록이_첫_항목이다() {
        Album album = Album.open(10L, "도쿄 3박 4일", JAPAN, 1L, 101L);

        assertThat(album.getGroupId()).isEqualTo(10L);
        assertThat(album.getRegion()).isSameAs(JAPAN);
        assertThat(album.getOpenerMemberId()).isEqualTo(1L);
        assertThat(album.getEntries())
                .extracting(AlbumEntry::getTravelRecordId, AlbumEntry::getMemberId)
                .containsExactly(tuple(101L, 1L));
        assertThat(album.travelRecordIds()).containsExactly(101L);
    }

    @Test
    void 이름_앞뒤_공백은_지운다() {
        assertThat(Album.open(10L, " 도쿄 ", JAPAN, 1L, 101L).getName()).isEqualTo("도쿄");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void 빈_이름은_거절한다(String name) {
        assertThatThrownBy(() -> Album.open(10L, name, JAPAN, 1L, 101L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(GroupErrorCode.INVALID_ALBUM_NAME);
    }

    @Test
    void 이름이_200자를_넘으면_거절한다() {
        assertThatThrownBy(() -> Album.open(10L, "가".repeat(201), JAPAN, 1L, 101L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(GroupErrorCode.INVALID_ALBUM_NAME);
    }
}
