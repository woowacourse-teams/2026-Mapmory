package com.mapmory.backend.travelrecord.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.mapmory.backend.common.exception.BusinessException;
import com.mapmory.backend.member.Member;
import com.mapmory.backend.region.domain.Region;
import com.mapmory.backend.region.domain.RegionType;
import com.mapmory.backend.tag.Tag;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class TravelRecordTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 26);
    private static final Member OWNER = member(10L);

    private static final String KEY_A = "travel-records/10/a.jpg";
    private static final String KEY_B = "travel-records/10/b.jpg";
    private static final String KEY_C = "travel-records/10/c.jpg";

    @Test
    void 요청_순서를_미디어_정렬_순서로_삼는다() {
        TravelRecord travelRecord = travelRecord();
        travelRecord.synchronizeMedia(List.of(KEY_A, KEY_B));

        travelRecord.synchronizeMedia(List.of(KEY_B, KEY_A));

        assertThat(travelRecord.getMedia())
                .extracting(RecordMedia::getObjectKey)
                .containsExactly(KEY_B, KEY_A);
        assertThat(travelRecord.getMedia())
                .extracting(RecordMedia::getSortOrder)
                .containsExactly(0, 1);
    }

    @Test
    void 요청에_없는_미디어는_컬렉션에서_떨어진다() {
        TravelRecord travelRecord = travelRecord();
        travelRecord.synchronizeMedia(List.of(KEY_A, KEY_B));

        travelRecord.synchronizeMedia(List.of(KEY_B));

        assertThat(travelRecord.getMedia())
                .extracting(RecordMedia::getObjectKey)
                .containsExactly(KEY_B);
    }

    @Test
    void 새_Object_Key는_미디어를_만들어_붙인다() {
        TravelRecord travelRecord = travelRecord();
        travelRecord.synchronizeMedia(List.of(KEY_A));

        travelRecord.synchronizeMedia(List.of(KEY_A, KEY_C));

        assertThat(travelRecord.getMedia())
                .extracting(RecordMedia::getObjectKey)
                .containsExactly(KEY_A, KEY_C);
        assertThat(travelRecord.getMedia().getLast().getSortOrder()).isEqualTo(1);
    }

    @Test
    void 빈_Object_Key_목록은_모든_미디어를_떼어낸다() {
        TravelRecord travelRecord = travelRecord();
        travelRecord.synchronizeMedia(List.of(KEY_A));

        travelRecord.synchronizeMedia(List.of());

        assertThat(travelRecord.getMedia()).isEmpty();
    }

    @Test
    void 한_일지_안에서_Object_Key가_중복되면_거부한다() {
        assertThatThrownBy(() -> TravelRecord.validateObjectKeys(List.of(KEY_A, KEY_A)))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode().code())
                .isEqualTo("INVALID_OBJECT_KEY");
    }

    @Test
    void 동기화도_중복_Object_Key를_거부한다() {
        TravelRecord travelRecord = travelRecord();

        assertThatThrownBy(() -> travelRecord.synchronizeMedia(List.of(KEY_A, KEY_A)))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode().code())
                .isEqualTo("INVALID_OBJECT_KEY");
    }

    @Test
    void 이미_가진_Object_Key는_새_키가_아니다() {
        TravelRecord travelRecord = travelRecord();
        travelRecord.synchronizeMedia(List.of(KEY_A));

        List<String> newObjectKeys = travelRecord.newObjectKeys(List.of(KEY_A, KEY_C));

        assertThat(newObjectKeys).containsExactly(KEY_C);
    }

    @Test
    void 요청한_태그를_붙인다() {
        TravelRecord travelRecord = travelRecordOf(OWNER);

        travelRecord.retag(RecordTagIds.from(List.of(2L, 1L)), List.of(tag(OWNER, 1L), tag(OWNER, 2L)));

        assertThat(travelRecord.getTags()).extracting(TravelRecordTag::tagId).containsExactly(2L, 1L);
    }

    @Test
    void 남아_있는_태그의_연결은_그대로_두고_빠진_것만_떼고_새것만_붙인다() {
        TravelRecord travelRecord = travelRecordOf(OWNER);
        travelRecord.retag(RecordTagIds.from(List.of(1L, 2L)), List.of(tag(OWNER, 1L), tag(OWNER, 2L)));
        TravelRecordTag kept = travelRecord.getTags().getFirst();

        travelRecord.retag(RecordTagIds.from(List.of(1L, 3L)), List.of(tag(OWNER, 1L), tag(OWNER, 3L)));

        assertThat(travelRecord.getTags()).extracting(TravelRecordTag::tagId).containsExactly(1L, 3L);
        assertThat(travelRecord.getTags().getFirst()).isSameAs(kept);
    }

    @Test
    void 빈_태그로_바꾸면_모든_연결을_뗀다() {
        TravelRecord travelRecord = travelRecordOf(OWNER);
        travelRecord.retag(RecordTagIds.from(List.of(1L)), List.of(tag(OWNER, 1L)));

        travelRecord.retag(RecordTagIds.from(List.of()), List.of());

        assertThat(travelRecord.getTags()).isEmpty();
    }

    @Test
    void 남의_태그는_붙이지_않는다() {
        TravelRecord travelRecord = travelRecordOf(OWNER);
        Member other = member(20L);

        assertTagError(
                () -> travelRecord.retag(RecordTagIds.from(List.of(1L)), List.of(tag(other, 1L))),
                "TAG_NOT_FOUND"
        );
        assertThat(travelRecord.getTags()).isEmpty();
    }

    @Test
    void 조회되지_않은_태그는_붙이지_않는다() {
        TravelRecord travelRecord = travelRecordOf(OWNER);
        travelRecord.retag(RecordTagIds.from(List.of(1L)), List.of(tag(OWNER, 1L)));

        assertTagError(
                () -> travelRecord.retag(RecordTagIds.from(List.of(1L, 2L)), List.of(tag(OWNER, 1L))),
                "TAG_NOT_FOUND"
        );
        assertThat(travelRecord.getTags()).extracting(TravelRecordTag::tagId).containsExactly(1L);
    }

    @Test
    void 제목이_null이나_공백이면_빈_문자열로_정규화한다() {
        TravelRecord nullTitleRecord = TravelRecord.of(
                mock(Member.class),
                Region.of(null, null, "JP", "일본", RegionType.COUNTRY),
                null,
                "본문",
                LocalDate.of(2026, 8, 11),
                null,
                TODAY
        );
        TravelRecord blankTitleRecord = TravelRecord.of(
                mock(Member.class),
                Region.of(null, null, "JP", "일본", RegionType.COUNTRY),
                "   ",
                "본문",
                LocalDate.of(2026, 8, 11),
                null,
                TODAY
        );

        assertThat(nullTitleRecord.getTitle()).isEmpty();
        assertThat(blankTitleRecord.getTitle()).isEmpty();
    }

    @Test
    void 대한민국_일지는_시도까지만_골라도_만든다() {
        Region korea = Region.of(null, null, "KR", "대한민국", RegionType.COUNTRY);
        Region busan = Region.of(korea, korea, "26", "부산광역시", RegionType.PROVINCE);

        TravelRecord travelRecord = TravelRecord.of(
                mock(Member.class), busan, "부산 여행", "본문", LocalDate.of(2026, 8, 11), null, TODAY);

        assertThat(travelRecord.getRegion()).isEqualTo(busan);
    }

    @Test
    void 대한민국_전체에는_일지를_붙이지_않는다() {
        Region korea = Region.of(null, null, "KR", "대한민국", RegionType.COUNTRY);

        assertThatThrownBy(() -> TravelRecord.of(
                mock(Member.class), korea, "여행", "본문", LocalDate.of(2026, 8, 11), null, TODAY))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode().code()).isEqualTo("REGION_REQUIRED"));
        TravelRecord travelRecord = travelRecord();
        assertThatThrownBy(() -> travelRecord.update(
                korea, "여행", "본문", LocalDate.of(2026, 8, 11), null, TODAY))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void 오늘까지의_기간으로는_일지를_만든다() {
        Region japan = Region.of(null, null, "JP", "일본", RegionType.COUNTRY);

        TravelRecord travelRecord = TravelRecord.of(
                mock(Member.class), japan, "여행", "본문", TODAY, TODAY, TODAY);

        assertThat(travelRecord.getStartDate()).isEqualTo(TODAY);
        assertThat(travelRecord.getEndDate()).isEqualTo(TODAY);
    }

    @Test
    void 오늘보다_뒤의_기간으로는_일지를_만들지_않는다() {
        Region japan = Region.of(null, null, "JP", "일본", RegionType.COUNTRY);
        LocalDate tomorrow = TODAY.plusDays(1);

        assertDateError(() -> TravelRecord.of(
                mock(Member.class), japan, "여행", "본문", tomorrow, null, TODAY));
        assertDateError(() -> TravelRecord.of(
                mock(Member.class), japan, "여행", "본문", TODAY, tomorrow, TODAY));
    }

    @Test
    void 오늘보다_뒤의_기간으로는_일지를_고치지_않는다() {
        TravelRecord travelRecord = travelRecord();
        Region region = travelRecord.getRegion();
        LocalDate tomorrow = TODAY.plusDays(1);

        assertDateError(() -> travelRecord.update(region, "바뀐 제목", "본문", tomorrow, null, TODAY));
        assertDateError(() -> travelRecord.update(region, "바뀐 제목", "본문", TODAY, tomorrow, TODAY));
        assertThat(travelRecord.getTitle()).isEqualTo("일본 여행");
        assertThat(travelRecord.getStartDate()).isEqualTo(LocalDate.of(2026, 8, 11));
    }

    private TravelRecord travelRecord() {
        Region region = Region.of(null, null, "JP", "일본", RegionType.COUNTRY);

        return TravelRecord.of(
                mock(Member.class),
                region,
                "일본 여행",
                "본문",
                LocalDate.of(2026, 8, 11),
                null,
                TODAY
        );
    }

    private TravelRecord travelRecordOf(Member owner) {
        return TravelRecord.of(
                owner,
                Region.of(null, null, "JP", "일본", RegionType.COUNTRY),
                "일본 여행",
                "본문",
                LocalDate.of(2026, 8, 11),
                null,
                TODAY
        );
    }

    private static Member member(Long id) {
        Member member = mock(Member.class);
        when(member.getId()).thenReturn(id);
        return member;
    }

    private Tag tag(Member owner, Long id) {
        Tag tag = Tag.of(owner, "태그" + id);
        ReflectionTestUtils.setField(tag, "id", id);
        return tag;
    }

    private void assertDateError(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode().code())
                .isEqualTo("INVALID_TRAVEL_DATE_RANGE");
    }

    private void assertTagError(Runnable action, String errorCode) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode().code())
                .isEqualTo(errorCode);
    }
}
