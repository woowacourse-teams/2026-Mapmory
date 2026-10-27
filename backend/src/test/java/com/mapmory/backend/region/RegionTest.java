package com.mapmory.backend.region;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RegionTest {

    private final Region korea = Region.of(null, null, "KR", "대한민국", RegionType.COUNTRY);
    private final Region seoul = Region.of(korea, korea, "11", "서울특별시", RegionType.PROVINCE);
    private final Region jongno = Region.of(seoul, korea, "11110", "종로구", RegionType.DISTRICT);

    @Test
    void 국가는_자기_자신이_국가이고_시도와_시군구가_없다() {
        assertThat(korea.country()).isSameAs(korea);
        assertThat(korea.province()).isNull();
        assertThat(korea.district()).isNull();
    }

    @Test
    void 시도는_국가와_자기_자신을_갖고_시군구가_없다() {
        assertThat(seoul.country()).isSameAs(korea);
        assertThat(seoul.province()).isSameAs(seoul);
        assertThat(seoul.district()).isNull();
    }

    @Test
    void 시군구는_국가와_시도와_자기_자신을_갖는다() {
        assertThat(jongno.country()).isSameAs(korea);
        assertThat(jongno.province()).isSameAs(seoul);
        assertThat(jongno.district()).isSameAs(jongno);
    }
}
