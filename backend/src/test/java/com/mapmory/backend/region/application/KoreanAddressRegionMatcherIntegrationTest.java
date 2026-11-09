package com.mapmory.backend.region.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.mapmory.backend.IntegrationTest;
import com.mapmory.backend.region.domain.Region;
import com.mapmory.backend.region.domain.RegionAddress;
import com.mapmory.backend.region.web.dto.RegionDetailResponse;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class KoreanAddressRegionMatcherIntegrationTest extends IntegrationTest {

    @Autowired
    KoreanAddressRegionMatcher matcher;

    @Test
    void 주소로_찾은_시군구는_트랜잭션_밖에서도_국가와_시도까지_응답할_수_있다() {
        Region district = matcher.match(new RegionAddress("KR", "서울특별시", List.of("종로구"))).orElseThrow();

        // 장소 선택 API는 트랜잭션 밖에서 응답을 만든다(open-in-view: false).
        RegionDetailResponse response = RegionDetailResponse.from(district);

        assertThat(response.country().code()).isEqualTo("KR");
        assertThat(response.province().code()).isEqualTo("11");
        assertThat(response.district().code()).isEqualTo("11110");
    }

    @Test
    void 시도가_없는_주소도_실제_지역_데이터에서_전국에_하나뿐인_시군구로_찾는다() {
        // Google 대천해수욕장 상세는 시·도 없이 보령시(locality)와 대한민국만 준다.
        Region district = matcher.match(new RegionAddress("KR", null, List.of("보령시"))).orElseThrow();

        RegionDetailResponse response = RegionDetailResponse.from(district);

        assertThat(response.province().code()).isEqualTo("44");
        assertThat(response.district().code()).isEqualTo("44180");
    }

    @Test
    void 독도_짧은_주소의_낱말로_실제_지역_데이터에서_울릉군을_찾는다() {
        Region district = matcher.match(new RegionAddress(null, null, List.of("울릉군", "울릉읍", "독도리"))).orElseThrow();

        RegionDetailResponse response = RegionDetailResponse.from(district);

        assertThat(response.province().code()).isEqualTo("47");
        assertThat(response.district().code()).isEqualTo("47940");
    }

    @Test
    void 시도가_없는_주소의_시군구_이름이_실제_지역_데이터에서_겹치면_고르지_않는다() {
        assertThat(matcher.match(new RegionAddress("KR", null, List.of("중구")))).isEmpty();
        assertThat(matcher.match(new RegionAddress("KR", null, List.of("고성군")))).isEmpty();
    }
}
