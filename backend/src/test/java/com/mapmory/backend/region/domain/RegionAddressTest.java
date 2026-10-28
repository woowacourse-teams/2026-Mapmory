package com.mapmory.backend.region.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

class RegionAddressTest {

    @Test
    void 빈_이름은_없는_값으로_보고_앞뒤_공백을_뗀다() {
        RegionAddress address = new RegionAddress("KR", " ", Arrays.asList(" 성남시 ", null, "", "분당구"));

        assertThat(address.province()).isNull();
        assertThat(address.subAreas()).containsExactly("성남시", "분당구");
    }

    @Test
    void 시군구_목록이_없으면_빈_목록이다() {
        assertThat(new RegionAddress("KR", "서울특별시", null).subAreas()).isEmpty();
    }
}
