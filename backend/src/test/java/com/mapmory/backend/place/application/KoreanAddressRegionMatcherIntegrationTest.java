package com.mapmory.backend.place.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.mapmory.backend.IntegrationTest;
import com.mapmory.backend.region.Region;
import com.mapmory.backend.travelrecord.dto.RegionDetailResponse;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class KoreanAddressRegionMatcherIntegrationTest extends IntegrationTest {

    @Autowired
    KoreanAddressRegionMatcher matcher;

    @Test
    void 주소로_찾은_시군구는_트랜잭션_밖에서도_국가와_시도까지_응답할_수_있다() {
        Region district = matcher.match(List.of("서울특별시", "종로구")).orElseThrow();

        // 장소 선택 API는 트랜잭션 밖에서 응답을 만든다(open-in-view: false).
        RegionDetailResponse response = RegionDetailResponse.from(district);

        assertThat(response.country().code()).isEqualTo("KR");
        assertThat(response.province().code()).isEqualTo("11");
        assertThat(response.district().code()).isEqualTo("11110");
    }
}
