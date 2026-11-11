package com.mapmory.backend.travelrecord.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PhotoCaptureDatesTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 11);
    private static final String KEY = "travel-records/10/a.jpg";

    @Test
    void 받은_촬영일을_Object_Key로_찾는다() {
        PhotoCaptureDates dates = PhotoCaptureDates.from(Map.of(KEY, LocalDate.of(2026, 9, 11)), TODAY);

        assertThat(dates.of(ObjectKey.from(KEY))).isEqualTo(LocalDate.of(2026, 9, 11));
    }

    @Test
    void 시간대_차이를_위해_내일까지는_받는다() {
        PhotoCaptureDates dates = PhotoCaptureDates.from(Map.of(KEY, TODAY.plusDays(1)), TODAY);

        assertThat(dates.of(ObjectKey.from(KEY))).isEqualTo(TODAY.plusDays(1));
    }

    @Test
    void 카메라_시계가_틀린_날짜는_모르는_날짜로_본다() {
        PhotoCaptureDates future = PhotoCaptureDates.from(Map.of(KEY, TODAY.plusDays(2)), TODAY);
        PhotoCaptureDates tooOld = PhotoCaptureDates.from(Map.of(KEY, LocalDate.of(1899, 12, 31)), TODAY);

        assertThat(future.of(ObjectKey.from(KEY))).isNull();
        assertThat(tooOld.of(ObjectKey.from(KEY))).isNull();
    }

    @Test
    void 형식이_틀린_키와_빈_날짜는_버린다() {
        Map<String, LocalDate> raw = new HashMap<>();
        raw.put(" ", LocalDate.of(2026, 9, 11));
        raw.put(KEY, null);

        PhotoCaptureDates dates = PhotoCaptureDates.from(raw, TODAY);

        assertThat(dates.of(ObjectKey.from(KEY))).isNull();
    }

    @Test
    void 촬영일을_보내지_않으면_비어_있다() {
        assertThat(PhotoCaptureDates.from(null, TODAY).of(ObjectKey.from(KEY))).isNull();
    }
}
