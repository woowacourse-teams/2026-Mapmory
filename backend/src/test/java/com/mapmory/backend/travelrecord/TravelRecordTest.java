package com.mapmory.backend.travelrecord;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.mapmory.backend.common.exception.BusinessException;
import com.mapmory.backend.member.Member;
import com.mapmory.backend.region.Region;
import com.mapmory.backend.region.RegionType;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;

class TravelRecordTest {

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
    void 태그를_다섯_개까지_붙일_수_있다() {
        TravelRecord travelRecord = travelRecord();

        assertThatCode(() -> travelRecord.validateTagIds(tagIds(5))).doesNotThrowAnyException();
    }

    @Test
    void 태그가_다섯_개를_넘으면_거부한다() {
        TravelRecord travelRecord = travelRecord();

        assertTagError(() -> travelRecord.validateTagIds(tagIds(6)), "TOO_MANY_TAGS");
    }

    @Test
    void 같은_태그를_두_번_붙이면_거부한다() {
        TravelRecord travelRecord = travelRecord();

        assertTagError(() -> travelRecord.validateTagIds(List.of(1L, 1L)), "VALIDATION_ERROR");
    }

    private TravelRecord travelRecord() {
        Region region = Region.of(null, null, "JP", "일본", RegionType.COUNTRY);

        return TravelRecord.of(
                mock(Member.class),
                region,
                "일본 여행",
                "본문",
                LocalDate.of(2026, 8, 11),
                null
        );
    }

    private List<Long> tagIds(int count) {
        return LongStream.rangeClosed(1, count).boxed().toList();
    }

    private void assertTagError(Runnable action, String errorCode) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode().code())
                .isEqualTo(errorCode);
    }
}
