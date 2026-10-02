package com.mapmory.backend.place.infrastructure.region;

import static org.assertj.core.api.Assertions.assertThat;

import com.mapmory.backend.place.application.port.DistrictLocator;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import tools.jackson.databind.json.JsonMapper;

class KoreanDistrictLocatorTest {

    private final KoreanDistrictLocator locator;

    KoreanDistrictLocatorTest() throws IOException {
        locator = new KoreanDistrictLocator(JsonMapper.builder().build());
    }

    @Test
    void 은평구_좌표를_내부_지역_코드로_변환한다() {
        assertThat(locator.find(126.929, 37.602))
                .contains(new DistrictLocator.DistrictMatch("11", "11380"));
    }

    @Test
    void 여의도한강공원_좌표를_영등포구로_변환한다() {
        assertThat(locator.find(126.932, 37.528))
                .contains(new DistrictLocator.DistrictMatch("11", "11560"));
    }

    @Test
    void 한국_밖의_좌표는_시군구를_추천하지_않는다() {
        assertThat(locator.find(139.6917, 35.6895)).isEmpty();
    }

    @Test
    void 경계_JSON이_없어도_수동_선택할_수_있다() throws IOException {
        KoreanDistrictLocator emptyLocator = new KoreanDistrictLocator(JsonMapper.builder().build(), new Resource[0]);

        assertThat(emptyLocator.find(126.932, 37.528)).isEmpty();
    }
}
