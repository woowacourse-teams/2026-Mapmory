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
    void 보낸_이름이_있으면_그_이름으로_연다() {
        assertThat(Album.nameFor("도쿄 3월", "시부야", JAPAN)).isEqualTo("도쿄 3월");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" "})
    void 이름을_비우면_기록_제목이고_제목도_비었으면_지역_이름이다(String blank) {
        assertThat(Album.nameFor(blank, "시부야", JAPAN)).isEqualTo("시부야");
        assertThat(Album.nameFor(blank, blank, JAPAN)).isEqualTo(JAPAN.getName());
    }

    @Test
    void 이름이_200자를_넘으면_거절한다() {
        assertThatThrownBy(() -> Album.open(10L, "가".repeat(201), JAPAN, 1L, 101L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(GroupErrorCode.INVALID_ALBUM_NAME);
    }

    @Test
    void 기록을_담으면_항목이_늘고_담은_기록을_찾을_수_있다() {
        Album album = Album.open(10L, "도쿄", JAPAN, 1L, 101L);

        assertThat(album.addEntry(2L, 202L)).isTrue();

        assertThat(album.travelRecordIds()).containsExactly(101L, 202L);
        assertThat(album.travelRecordIdsOf(2L)).containsExactly(202L);
        assertThat(album.travelRecordIdsOf(3L)).isEmpty();
    }

    @Test
    void 같은_기록을_다시_담으면_아무것도_바꾸지_않는다() {
        Album album = Album.open(10L, "도쿄", JAPAN, 1L, 101L);

        assertThat(album.addEntry(1L, 101L)).isFalse();

        assertThat(album.getEntries()).hasSize(1);
    }

    @Test
    void 같은_멤버가_기록을_여럿_담는다() {
        Album album = Album.open(10L, "도쿄", JAPAN, 1L, 101L);

        assertThat(album.addEntry(1L, 102L)).isTrue();
        assertThat(album.addEntry(2L, 201L)).isTrue();

        assertThat(album.travelRecordIdsOf(1L)).containsExactly(101L, 102L);
        assertThat(album.travelRecordIdsOf(2L)).containsExactly(201L);
        assertThat(album.travelRecordIds()).containsExactly(101L, 102L, 201L);
    }

    @Test
    void 이름을_바꿀_때도_같은_규칙이다() {
        Album album = Album.open(10L, "도쿄", JAPAN, 1L, 101L);

        album.rename("  도쿄 3월 ");

        assertThat(album.getName()).isEqualTo("도쿄 3월");
        assertThatThrownBy(() -> album.rename("가".repeat(201)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(GroupErrorCode.INVALID_ALBUM_NAME);
        assertThat(album.getName()).isEqualTo("도쿄 3월");
    }

    @Test
    void 지워진_기록의_항목을_떼고_다_떼면_비었다() {
        Album album = Album.open(10L, "도쿄", JAPAN, 1L, 101L);
        album.addEntry(2L, 201L);

        assertThat(album.removeEntriesOf(201L)).isTrue();
        assertThat(album.travelRecordIds()).containsExactly(101L);
        assertThat(album.isEmpty()).isFalse();

        assertThat(album.removeEntriesOf(999L)).isFalse();
        assertThat(album.removeEntriesOf(101L)).isTrue();
        assertThat(album.isEmpty()).isTrue();
    }

    @Test
    void 지역을_바꾼다() {
        Album album = Album.open(10L, "도쿄", JAPAN, 1L, 101L);
        Region osaka = Region.of(JAPAN, JAPAN, "27", "오사카부", RegionType.PROVINCE);

        album.changeRegion(osaka);

        assertThat(album.getRegion()).isSameAs(osaka);
    }
}
